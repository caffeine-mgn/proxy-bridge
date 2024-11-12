package pw.binom.frame.virtual

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import pw.binom.ByteBufferPool
import pw.binom.ChannelId
import pw.binom.atomic.AtomicBoolean
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.crc.CRC32
import pw.binom.frame.AbstractByteBufferFrameInput
import pw.binom.frame.AbstractByteBufferFrameOutput
import pw.binom.frame.FrameChannel
import pw.binom.frame.FrameInput
import pw.binom.frame.FrameOutput
import pw.binom.frame.FrameResult
import pw.binom.frame.PackageSize
import pw.binom.io.ByteBuffer
import pw.binom.io.holdState
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logging.withVariables
import pw.binom.readShort
import pw.binom.writeShort
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

class VirtualChannelManagerImpl2(
    val bufferSize: PackageSize,
    val channelEmitted: (VirtualChannel) -> Unit,
    val bytePool: ByteBufferPool,
) {

    companion object {
        const val DATA: Byte = 0x1
        const val DATA_START: Byte = 0x2
        const val CLOSE: Byte = 0x3
    }

    private val channels = HashMap<Short, VC>()
    private val channelLock = SpinLock()
    private val logger by Logger.ofThisOrGlobal

    fun getOrCreate(id: ChannelId): FrameChannel = channelLock.synchronize {
        channels[id.raw]?.let { return@synchronize it }
        val newChannel = VC(id = id, bufferSize = bufferSize)
        channels[id.raw] = newChannel
        newChannel
    }

    private class BufferFrameInput(override val buffer: ByteBuffer) : AbstractByteBufferFrameInput()

    private class BufferFrameOutput(override val buffer: ByteBuffer) : AbstractByteBufferFrameOutput()

    private val internalOutcomeChannel = Channel<ByteBuffer>(onUndeliveredElement = { it.close() })
    private val internalIncomeChannel = Channel<ByteBuffer>(onUndeliveredElement = { it.close() })

    val income: SendChannel<ByteBuffer>
        get() = internalIncomeChannel

    val outcome: ReceiveChannel<ByteBuffer>
        get() = internalOutcomeChannel

    suspend fun processing() {
        while (coroutineContext.isActive) {
            val buf = try {
                internalIncomeChannel.receive()
            } catch (_: ClosedReceiveChannelException) {
                break
            } catch (_: CancellationException) {
                break
            }

            val cmd = buf.getByte()
            when (cmd) {
                DATA_START, DATA -> {
                    val channelId = buf.readShort()
                    val frameId = FrameId(buf.getByte())
                    var exist = channelLock.synchronize { channels[channelId] }
                    if (exist == null && !frameId.isInit) {
                        logger.info("Skip frame ${frameId.asShortString} because channel already closed")
                        buf.close()
                        sendClosed(ChannelId(channelId))
                    } else {
                        if (exist == null) {
                            val newChannel = getOrCreate(ChannelId(channelId)) as VC
                            exist = newChannel
                            channelEmitted(newChannel)
                        }
                        try {
                            exist.incomePackage(id = frameId, data = buf)
                        } catch (_: ClosedSendChannelException) {
                            buf.close()
                        } catch (_: CancellationException) {
                            buf.close()
                        } catch (e: Throwable) {
                            buf.close()
                            e.printStackTrace()
                        }
                    }
                }

                CLOSE -> {
                    val channelId = buf.readShort()
                    logger.info("Closing channel ${ChannelId(channelId)}")
                    channelLock.synchronize { channels.remove(channelId) }?.asyncClose()
                }
            }
        }
    }

    private suspend fun sendClosed(channelId: ChannelId) {
        val buffer = bytePool.borrow()
        buffer.put(CLOSE)
        buffer.writeShort(channelId.raw)
        buffer.flip()
        internalOutcomeChannel.send(buffer)
    }

    private inner class VC(override val id: ChannelId, bufferSize: PackageSize) : VirtualChannel {
        private val logger = Logger.getLogger("VirtualChannel").withVariables("virtual-channel" to id.raw.toString())
        override val bufferSize: PackageSize = bufferSize - 1 - Short.SIZE_BYTES - 1
        private val incomeChannel = Channel<ByteBuffer>(onUndeliveredElement = { it.close() })
        private val notInTimePackages = HashMap<Byte, ByteBuffer>()
        private var packageCounter = FrameId.INIT
        private var lastIncomeFrameId = FrameId.INIT.previous
        private val lock = SpinLock()
        private var closed = false
        private val closeSent = AtomicBoolean(false)

        @OptIn(ExperimentalStdlibApi::class)
        suspend fun incomePackage(id: FrameId, data: ByteBuffer) {
            lock.synchronize {
                if (closed) {
                    data.close()
                    return
                }
            }
            if (lastIncomeFrameId.next == id) {
                val c = CRC32()
                c.update(data.toByteArray())
                val remaining = data.remaining
                incomeChannel.send(data)
                lastIncomeFrameId = id
                logger.info(
                    "Frame ${id.asShortString} received. $remaining bytes. CRC: ${
                        c.finish().toHexString()
                    }"
                )
                do {
                    val n = lastIncomeFrameId.next
                    val exist = lock.synchronize {
                        val l = notInTimePackages.remove(n.raw)
                        if (closed) {
                            l?.close()
                            return
                        }
                        l
                    } ?: break
                    val c = CRC32()
                    c.update(exist.toByteArray())
                    logger.info(
                        "Frame ${n.asShortString} recovered and placed to income channel. ${exist.remaining} bytes. CRC: ${
                            c.finish().toHexString()
                        }"
                    )
                    incomeChannel.send(exist)
                    lock.synchronize {
                        lastIncomeFrameId = n
                    }
                } while (true)
            } else {
                val c = CRC32()
                c.update(data.toByteArray())
                logger.info(
                    "Package from feature ${id.asShortString}. Expect frame ${lastIncomeFrameId.next.asShortString}. ${data.remaining} bytes. CRC: ${
                        c.finish().toHexString()
                    }"
                )
                lock.synchronize {
                    if (closed) {
                        data.close()
                        return
                    }
                    notInTimePackages[id.raw] = data
                }
            }
        }

        private val firstFrameFlag = AtomicBoolean(true)

        @OptIn(ExperimentalStdlibApi::class)
        override suspend fun <T> sendFrame(func: (FrameOutput) -> T): FrameResult<T> {
            if (lock.synchronize { closed }) {
                return FrameResult.closed()
            }
            val frameId = packageCounter
            packageCounter = packageCounter.next
            val buf = ByteBuffer(bufferSize.asInt)//bytePool.borrow()
            val dataByte = if (firstFrameFlag.compareAndSet(false, true)) {
                DATA_START
            } else {
                DATA
            }
            buf.put(dataByte)
            buf.writeShort(id.raw)
            buf.put(frameId.asByte)
            val out = BufferFrameOutput(buf)
            val result = try {
                func(out)
            } catch (e: Throwable) {
                buf.close()
                throw e
            }
            buf.flip()
            try {
                val crc = buf.holdState {
                    it.position = 1 + 2 + 1
                    val c = CRC32()
                    c.update(it.toByteArray())
                    c.finish().toHexString()
                }
                logger.info("Send frame $frameId. ${buf.remaining - 4} bytes. CRC: $crc")
                internalOutcomeChannel.send(buf)
            } catch (_: ClosedSendChannelException) {
                buf.close()
                return FrameResult.Companion.closed()
            } catch (_: CancellationException) {
                buf.close()
                return FrameResult.Companion.closed()
            } catch (e: Throwable) {
                buf.close()
                throw e
            }
            return FrameResult.Companion.of(result)
        }

        override suspend fun <T> readFrame(func: (FrameInput) -> T): FrameResult<T> {
            val data = try {
                incomeChannel.receive()
            } catch (_: ClosedReceiveChannelException) {
                return FrameResult.Companion.closed()
            } catch (_: CancellationException) {
                return FrameResult.Companion.closed()
            }
            return try {
                FrameResult.Companion.of(func(BufferFrameInput(data)))
            } finally {
                data.close()
            }
        }

        fun close() {
            lock.synchronize {
                if (closed) {
                    return
                }
                notInTimePackages.forEach {
                    it.value.close()
                }
                notInTimePackages.clear()
            }
            incomeChannel.close()
        }

        override suspend fun asyncClose() {
            logger.info("Closing channel")
            if (closeSent.compareAndSet(false, true)) {
                sendClosed(id)
            }
            close()
        }
    }
}
