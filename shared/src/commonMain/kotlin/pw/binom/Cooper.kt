package pw.binom

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.crc.CRC32
import pw.binom.frame.FrameChannel
import pw.binom.frame.FrameChannelWithMeta
import pw.binom.io.*
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.network.SocketClosedException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

object Cooper {
    private val logger by Logger.ofThisOrGlobal
    enum class CloseReason {
        FRAME_CLOSED,
        CHANNEL_CLOSED,
    }

    data class ExchangeResult(
        val reason: CloseReason,
        val frameWrite: DataTransferSize?,
        val streamWrite: DataTransferSize?,
        val frameCrc: Hash,
        val streamCrc: Hash,
    )

    suspend fun exchange(
        channelName: String,
        stream: AsyncChannel,
        frame: FrameChannelWithMeta,
        ctx: CoroutineContext = EmptyCoroutineContext,
    ): ExchangeResult {
        frame.meta["cooper"] = "#1"
        var waiter: CancellableContinuation<CloseReason>? = null
        val lock = SpinLock()
        var frameWrite: DataTransferSize? = null
        var streamWrite: DataTransferSize? = null
        val frameCrc = CRC32()
        val streamCrc = CRC32()
//        val frameData = ByteArrayOutput()
//        val streamData = ByteArrayOutput()
        frame.meta["cooper"] = "#2"
        val job1 = GlobalScope.launch(ctx + CoroutineName("$channelName stream->frame"), start = CoroutineStart.LAZY) {
            try {
                frame.meta["job1"] = "#1"
                frame.useAsync { frame ->
                    stream.useAsync { stream ->
                        frame.meta["job1"] = "#2"
                        frameWrite = stream.copyTo(frameChannel = frame, bufferHook = {
                            logger.info("Coping ${it.remaining} bytes")
                            it.holdState {
                                frameCrc.update(it)
                            }
//                            it.holdState {
//                                frameData.write(it)
//                            }
                        })
                        frame.meta["job1"] = "#3"
                    }
                }
            } finally {
                frame.meta["job1"] = "#4"
                val ww = lock.synchronize {
                    val l = waiter
                    waiter = null
                    l
                }
                frame.meta["job1"] = "#5"
                ww?.resume(CloseReason.CHANNEL_CLOSED)
                frame.meta["job1"] = "#6"
            }
        }
        frame.meta["cooper"] = "#3"
        val job2 = GlobalScope.launch(ctx+CoroutineName("$channelName frame->stream"), start = CoroutineStart.LAZY) {
            try {
                frame.meta["job2"] = "#1"
                frame.useAsync { frame ->
                    stream.useAsync { stream ->
                        frame.meta["job2"] = "#2"
                        streamWrite = frame.copyTo(stream, bufferHook = {
                            logger.info("Coping ${it.remaining} bytes")
                            it.holdState {
                                streamCrc.update(it)
                            }
//                            it.holdState {
//                                streamData.write(it)
//                            }
                        })
                        frame.meta["job2"] = "#3"
                    }
                }
            } finally {
                frame.meta["job2"] = "#4"
                val ww = lock.synchronize {
                    val l = waiter
                    waiter = null
                    l
                }
                frame.meta["job2"] = "#5"
                ww?.resume(CloseReason.FRAME_CLOSED)
                frame.meta["job2"] = "#6"
            }
        }
        frame.meta["cooper"] = "#4"
        val r = suspendCancellableCoroutine<CloseReason> {
            waiter = it
            job1.start()
            job2.start()
        }
        frame.meta["cooper"] = "#5"
        job1.cancelAndJoin()
        frame.meta["cooper"] = "#6"
        job1.cancelAndJoin()
        frame.meta["cooper"] = "#7"
//        println("FrameData: ${frameData.locked { it.toByteArray().toHexString() }}")
//        println("StreamData: ${streamData.locked { it.toByteArray().toHexString() }}")
//        println("frameData.size: ${frameData.size}")
//        println("streamData.size: ${streamData.size}")
        return ExchangeResult(
            reason = r,
            frameWrite = frameWrite,
            streamWrite = streamWrite,
            frameCrc = Hash(frameCrc.finish()),
            streamCrc = Hash(streamCrc.finish()),
        )
    }
}

suspend fun FrameChannel.copyTo(
    to: AsyncOutput,
    bufferHook: (suspend (ByteBuffer) -> Unit)? = null
): DataTransferSize {
    var size = 0
    byteBuffer(this.bufferSize.asInt).use { buffer ->
        while (currentCoroutineContext().isActive) {
            val copyResult = //SlowCoroutineDetect.detect("FrameChannel.copyTo(AsyncOutput) read from channel") {
                readFrame { buf2 ->
                    buffer.clear()
                    val e = buf2.readInto(buffer)
                    e
                }.valueOrNull
                //}
                    ?: break
            if (copyResult > 0) {
                buffer.flip()
                bufferHook?.invoke(buffer)
                try {
                    SlowCoroutineDetect.detect("FrameChannel.copyTo(AsyncOutput) write from stream") {
                        size += buffer.remaining
                        to.writeFully(buffer)
                        to.flush()
                    }
                } catch (_: SocketClosedException) {
                    break
                }
            }
        }
    }
    return DataTransferSize.ofSize(size)
}

suspend fun AsyncInput.copyTo(
    frameChannel: FrameChannel,
    bufferHook: (suspend (ByteBuffer) -> Unit)? = null
): DataTransferSize {
    var size = 0
    byteBuffer(frameChannel.bufferSize.asInt).use { buffer ->
        while (currentCoroutineContext().isActive) {
            buffer.clear()
            val l = try {
//                SlowCoroutineDetect.detect("AsyncInput.copyTo(FrameChannel) read from stream") {
                read(buffer)

//                }
            } catch (e: SocketClosedException) {
                break
            }
            if (l.isAvailable) {
                buffer.flip()
                bufferHook?.invoke(buffer)
                while (buffer.hasRemaining) {
                    val copyResult = SlowCoroutineDetect.detect("AsyncInput.copyTo(FrameChannel) write from channel") {
                        frameChannel.sendFrame { frameOut ->
                            frameOut.writeFrom(buffer)
                        }.valueOrNull
                    } ?: break
                    size += copyResult
                }
            } else {
                break
            }
        }
    }
    return DataTransferSize.ofSize(size)
}
