package pw.binom.frame.virtual

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import pw.binom.*
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

    private val channels = HashMap<ChannelId, VirtualChannelImpl>()
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
        channels[id]?.let { return@synchronize it }
        val newChannel = VirtualChannelImpl(
            id = id,
            bufferSize = bufferSize,
//            internalOutcomeChannel = internalOutcomeChannel,
            sender = channelFrameSender(id),
            closeFunc = this::sendChannelClose,
            disposeFunc = { channelId ->
                channelLock.synchronize {
                    channels.remove(channelId)
                }?.asyncClose()
            }
        )
        channels[id] = newChannel
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
                val channelId = ChannelId.read(buf)
                val frameId = FrameId(buf.getByte())
                var exist = channelLock.synchronize { channels[channelId] }
//                val channelId2 = ChannelId(channelId)
                if (exist == null && !frameId.isInit) {
                    logger.info("Skip frame ${frameId.asShortString} because channel already closed")
                    buf.close()
                    sendChannelClose(channelId)
                } else {
                    if (exist == null) {
                        logger.info("Channel not exist. Creating")
                        val newChannel = getOrCreate(channelId) as VirtualChannelImpl
                        logger.info("Channel created! Try to run init function")
                        exist = newChannel
                        try {
                            channelEmitted(newChannel)
                            logger.info("Init function for channel $channelId success finished!")
                        } catch (e: Throwable) {
                            logger.warn(text = "Error on start init function", exception = e)
                            buf.close()
                            return
                        }
                    }
                    try {
//                        logger.info("Send new data to channel $channelId2...")
                        val sendSuccess = withTimeoutOrNull(5.seconds) {
//                            SlowCoroutineDetect.detect("Push data to channel") {
                            exist.incomePackage(id = frameId, data = buf)
//                            }
                        }
                        if (sendSuccess != null) {
                            if (!sendSuccess) {
                                logger.info("New data to channel $channelId, but channel closed!")
                                SlowCoroutineDetect.detect("Sending close #1") {
                                    sendChannelClose(channelId)
                                }
                                buf.closeAnyway()
                            } else {
//                                logger.info("New data to channel $channelId2 success sent")
                            }

                        } else {
                            buf.closeAnyway()
                            logger.info("New data to channel $channelId processing timeout")
                        }
                    } catch (_: ClosedSendChannelException) {
                        logger.info("Error on income processing! 1")
                        buf.close()
                        SlowCoroutineDetect.detect("Sending close #2") {
                            sendChannelClose(channelId)
                        }
                    } catch (_: CancellationException) {
                        logger.info("Error on income processing! 2")
                        buf.closeAnyway()
                        SlowCoroutineDetect.detect("Sending close #3") {
                            sendChannelClose(channelId)
                        }
                        logger.info("Error on income processing! 3")
                    } catch (e: Throwable) {
                        logger.info("Error on income processing! 4")
                        buf.closeAnyway()
                    }
                }
            }

            CLOSE -> {
                val channelId = buf.readShort()
                buf.closeAnyway()
                val channel = channelLock.synchronize { channels[ChannelId(channelId)] }
                if (channel == null) {
                    logger.info("Can't close channel ${ChannelId(channelId)}. Not exist")
                } else {
                    logger.info("Closing channel ${ChannelId(channelId)}")
                    channelLock.synchronize { channels[ChannelId(channelId)] }?.closeReceived()
                }
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
            val buf = byteBuffer(bufferSize.asInt)
            buf.put(DATA)
            id.write(buf)
            val r = try {
                func(BufferFrameOutput(buf))
            } catch (e: Throwable) {
                buf.close()
                throw e
            }
            buf.flip()
            SlowCoroutineDetect.detect("Push message for send") {
                internalOutcomeChannel.send(buf)
            }
            return FrameResult.of(r)
        }

        override val bufferSize: PackageSize
            get() = this@VirtualChannelManagerImpl2.bufferSize - 1 - ChannelId.SIZE_BYTES - 1

        override suspend fun asyncClose() {
            TODO("Not yet implemented")
        }
    }

    private suspend fun sendChannelClose(id: ChannelId) {
        val buffer = byteBuffer(ChannelId.SIZE_BYTES + 1)
        buffer.put(CLOSE)
        id.write(buffer)
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
