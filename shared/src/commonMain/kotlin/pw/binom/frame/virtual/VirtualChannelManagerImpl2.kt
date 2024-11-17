package pw.binom.frame.virtual

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import pw.binom.ByteBufferPool
import pw.binom.ChannelId
import pw.binom.atomic.AtomicBoolean
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.crc.CRC32
import pw.binom.frame.*
import pw.binom.io.ByteBuffer
import pw.binom.io.holdState
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.warn
import pw.binom.metric.AsyncMetricVisitor
import pw.binom.metric.DurationRollingAverageGauge
import pw.binom.metric.Metric
import pw.binom.metric.MetricProviderImpl
import pw.binom.metric.MetricVisitor
import pw.binom.readShort
import pw.binom.writeShort
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class VirtualChannelManagerImpl2(
    val bufferSize: PackageSize,
    val channelEmitted: (VirtualChannel) -> Unit,
    val bytePool: ByteBufferPool,
) : Metric {

    companion object {
        const val DATA: Byte = 0x1
        const val DATA_START: Byte = 0x2
        const val CLOSE: Byte = 0x3
    }

    private val channels = HashMap<Short, VC>()
    private val channelLock = SpinLock()
    private val logger by Logger.ofThisOrGlobal

    private val readDuration = DurationRollingAverageGauge(
        name = "manager_read_duration",
        windowSize = 50,
        unit = DurationUnit.MILLISECONDS
    )
    private val writeDuration = DurationRollingAverageGauge(
        name = "manager_write_duration",
        windowSize = 50,
        unit = DurationUnit.MILLISECONDS
    )

    fun getOrCreate(id: ChannelId): FrameChannel = channelLock.synchronize {
        channels[id.raw]?.let { return@synchronize it }
        val newChannel = VC(
            id = id,
            bufferSize = bufferSize,
            internalOutcomeChannel = internalOutcomeChannel,
        )
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

    private suspend fun processingOneMessage(buf: ByteBuffer) {
        val cmd = buf.getByte()
        when (cmd) {
            DATA_START, DATA -> {
                val channelId = buf.readShort()
                val frameId = FrameId(buf.getByte())
                var exist = channelLock.synchronize { channels[channelId] }
                val channelId2 = ChannelId(channelId)
                if (exist == null && !frameId.isInit) {
                    logger.info("Skip frame ${frameId.asShortString} because channel already closed")
                    buf.close()
                    VC.sendClosed(
                        channelId = channelId2,
                        internalOutcomeChannel = internalOutcomeChannel,
                    )
                } else {
                    if (exist == null) {
                        logger.info("Channel not exist. Creating")
                        val newChannel = getOrCreate(channelId2) as VC
                        logger.info("Channel created! Try to run init function")
                        exist = newChannel
                        try {
                            channelEmitted(newChannel)
                            logger.info("Init function for channel $channelId2 success finished!")
                        } catch (e: Throwable) {
                            logger.warn(text = "Error on start init function", exception = e)
                            buf.close()
                        }
                    }
                    try {
                        logger.info("Send new data to channel $channelId2...")
                        val sendSuccess = withTimeoutOrNull(5.seconds) {
                            exist.incomePackage(id = frameId, data = buf)
                        } != null
                        if (sendSuccess) {
                            logger.info("New data to channel $channelId2 success sent")
                        } else {
                            buf.closeAnyway()
                            logger.info("New data to channel $channelId2 processing timeout")
                        }
                    } catch (_: ClosedSendChannelException) {
                        logger.info("Error on income processing! 1")
                        buf.close()
                        VC.sendClosed(
                            channelId = ChannelId(channelId),
                            internalOutcomeChannel = internalOutcomeChannel,
                        )
                    } catch (_: CancellationException) {
                        logger.info("Error on income processing! 2")
                        buf.close()
                        VC.sendClosed(
                            channelId = ChannelId(channelId),
                            internalOutcomeChannel = internalOutcomeChannel,
                        )
                        logger.info("Error on income processing! 3")
                    } catch (e: Throwable) {
                        logger.info("Error on income processing! 4")
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

    suspend fun processing() {
        while (coroutineContext.isActive) {
            val buf = try {
                internalIncomeChannel.receive()
            } catch (_: ClosedReceiveChannelException) {
                logger.info("Income channel closed!")
                break
            } catch (_: CancellationException) {
                logger.info("Income processing finished!")
                break
            }
            processingOneMessage(buf)
        }
    }

    override fun accept(visitor: MetricVisitor) {
        readDuration.accept(visitor)
        writeDuration.accept(visitor)
        channelLock.synchronize {
            channels.values.forEach {
                it.accept(visitor)
            }
        }
    }

    override suspend fun accept(visitor: AsyncMetricVisitor) {
        readDuration.accept(visitor)
        writeDuration.accept(visitor)
        channelLock.synchronize {
            channels.values.forEach {
                it.accept(visitor)
            }
        }
    }

    private class VC(
        override val id: ChannelId,
        bufferSize: PackageSize,
        val internalOutcomeChannel: SendChannel<ByteBuffer>,
//        val sender: FrameSender
    ) : VirtualChannel, Metric {

        companion object {
            suspend fun sendClosed(channelId: ChannelId, internalOutcomeChannel: SendChannel<ByteBuffer>) {
                val buffer = ByteBuffer(Short.SIZE_BYTES + 1)
                buffer.put(CLOSE)
                buffer.writeShort(channelId.raw)
                buffer.flip()
                internalOutcomeChannel.send(buffer)
            }
        }

        private val logger = Logger.getLogger("VirtualChannel")//.withVariables("virtual-channel" to id.raw.toString())
        override val bufferSize: PackageSize = bufferSize - 1 - Short.SIZE_BYTES - 1
        private val incomeChannel = Channel<ByteBuffer>(onUndeliveredElement = { it.close() })
        private val notInTimePackages = HashMap<Byte, ByteBuffer>()
        private var packageCounter = FrameId.INIT
        private var lastIncomeFrameId = FrameId.INIT.previous
        private val lock = SpinLock()
        private var closed = false
        private val closeSent = AtomicBoolean(false)
        private val metricProvider = MetricProviderImpl()
        private val invalidFrameOrderCounter = metricProvider.counterLong("channel_invalid_frame_order")

        private val readDuration = DurationRollingAverageGauge(
            name = "channel_read_duration",
            windowSize = 50,
            unit = DurationUnit.MILLISECONDS
        )
        private val writeDuration = DurationRollingAverageGauge(
            name = "channel_write_duration",
            windowSize = 50,
            unit = DurationUnit.MILLISECONDS
        )

        override fun accept(visitor: MetricVisitor) {
            val v = visitor.withField("channel", id.toUShort.toString())
            metricProvider.accept(v)
            readDuration.accept(v)
            writeDuration.accept(v)
        }

        override suspend fun accept(visitor: AsyncMetricVisitor) {
            val v = visitor.withField("channel", id.toUShort.toString())
            metricProvider.accept(v)
            readDuration.accept(v)
            writeDuration.accept(v)
        }

        @OptIn(ExperimentalStdlibApi::class)
        suspend fun incomePackage(id: FrameId, data: ByteBuffer) {
            lock.synchronize {
                if (closed) {
                    data.close()
                    throw ClosedSendChannelException("Virtual Channel is closed!")
                }
            }
            if (lastIncomeFrameId.next == id) {
                incomeChannel.send(data)
                lastIncomeFrameId = id
                do {
                    val n = lastIncomeFrameId.next
                    val exist = lock.synchronize {
                        val l = notInTimePackages.remove(n.raw)
                        if (closed) {
                            l?.close()
                            throw ClosedSendChannelException("Virtual Channel is closed!")
                            return
                        }
                        l
                    } ?: break
                    incomeChannel.send(exist)
                    lock.synchronize {
                        lastIncomeFrameId = n
                    }
                } while (true)
            } else {
                invalidFrameOrderCounter.inc()
                val c = CRC32()
                c.update(data.toByteArray())
                lock.synchronize {
                    if (closed) {
                        data.close()
                        return
                    }
                    notInTimePackages[id.raw] = data
                }
            }
        }

        @OptIn(ExperimentalStdlibApi::class)
        override suspend fun <T> sendFrame(func: (FrameOutput) -> T): FrameResult<T> {
            if (lock.synchronize { closed }) {
                return FrameResult.closed()
            }
            val frameId = packageCounter
            packageCounter = packageCounter.next
            val buf = ByteBuffer(bufferSize.asInt)//bytePool.borrow()
            buf.put(DATA)
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
//                val crc = buf.holdState {
//                    it.position = 1 + 2 + 1
//                    val c = CRC32()
//                    c.update(it.toByteArray())
//                    c.finish().toHexString()
//                }

                val time = measureTime {
                    internalOutcomeChannel.send(buf)
                }
                writeDuration.put(time)
            } catch (_: ClosedSendChannelException) {
                buf.close()
                return FrameResult.closed()
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
                val m = measureTimedValue {
                    FrameResult.of(func(BufferFrameInput(data)))
                }
                readDuration.put(m.duration)
                m.value
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
            if (closeSent.compareAndSet(false, true)) {
                sendClosed(id, internalOutcomeChannel)
            }
            close()
        }
    }
}
