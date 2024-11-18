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

    private val channels = HashMap<Short, VirtualChannelImpl>()
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
        val newChannel = VirtualChannelImpl(
            id = id,
            bufferSize = bufferSize,
//            internalOutcomeChannel = internalOutcomeChannel,
            sender = channelFrameSender(id),
            closeFunc = this::sendChannelClose,
        )
        channels[id.raw] = newChannel
        newChannel
    }


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
                    sendChannelClose(ChannelId(channelId))
                } else {
                    if (exist == null) {
                        logger.info("Channel not exist. Creating")
                        val newChannel = getOrCreate(channelId2) as VirtualChannelImpl
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
                        }
                        if (sendSuccess != null) {
                            if (!sendSuccess) {
                                logger.info("New data to channel $channelId2, but channel closed!")
                                sendChannelClose(ChannelId(channelId))
                            } else {
                                logger.info("New data to channel $channelId2 success sent")
                            }

                        } else {
                            buf.closeAnyway()
                            logger.info("New data to channel $channelId2 processing timeout")
                        }
                    } catch (_: ClosedSendChannelException) {
                        logger.info("Error on income processing! 1")
                        buf.close()
                        sendChannelClose(ChannelId(channelId))
                    } catch (_: CancellationException) {
                        logger.info("Error on income processing! 2")
                        buf.close()
                        sendChannelClose(ChannelId(channelId))
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
//        channelLock.synchronize {
//            channels.values.forEach {
//                it.accept(visitor)
//            }
//        }
    }

    override suspend fun accept(visitor: AsyncMetricVisitor) {
        readDuration.accept(visitor)
        writeDuration.accept(visitor)
//        channelLock.synchronize {
//            channels.values.forEach {
//                it.accept(visitor)
//            }
//        }
    }

    private fun channelFrameSender(id: ChannelId) = object : FrameSender {
        override suspend fun <T> sendFrame(func: (buffer: FrameOutput) -> T): FrameResult<T> {
            val buf = ByteBuffer(bufferSize.asInt)
            buf.put(DATA)
            buf.writeShort(id.raw)
            val r = try {
                func(BufferFrameOutput(buf))
            } catch (e: Throwable) {
                buf.close()
                throw e
            }
            buf.flip()
            internalOutcomeChannel.send(buf)
            return FrameResult.of(r)
        }

        override val bufferSize: PackageSize
            get() = this@VirtualChannelManagerImpl2.bufferSize - 1 - Short.SIZE_BYTES - 1

        override suspend fun asyncClose() {
            TODO("Not yet implemented")
        }
    }

    private suspend fun sendChannelClose(id: ChannelId) {
        val buffer = ByteBuffer(Short.SIZE_BYTES + 1)
        buffer.put(CLOSE)
        buffer.writeShort(id.raw)
        buffer.flip()
        internalOutcomeChannel.send(buffer)
    }
    /*
        private class VC(

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
        }
        */
}
