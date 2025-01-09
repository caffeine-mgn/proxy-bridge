package pw.binom

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.crc.CRC32
import pw.binom.frame.FrameChannel
import pw.binom.io.*
import pw.binom.network.SocketClosedException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume

object Cooper {
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
        stream: AsyncChannel,
        frame: FrameChannel,
        ctx: CoroutineContext = EmptyCoroutineContext,
    ): ExchangeResult {
        var waiter: CancellableContinuation<CloseReason>? = null
        val lock = SpinLock()
        var frameWrite: DataTransferSize? = null
        var streamWrite: DataTransferSize? = null
        val frameCrc = CRC32()
        val streamCrc = CRC32()
//        val frameData = ByteArrayOutput()
//        val streamData = ByteArrayOutput()
        val job1 = GlobalScope.launch(ctx, start = CoroutineStart.LAZY) {
            try {
                frame.useAsync { frame ->
                    stream.useAsync { stream ->
                        frameWrite = stream.copyTo(frame, bufferHook = {
                            it.holdState {
                                frameCrc.update(it)
                            }
//                            it.holdState {
//                                frameData.write(it)
//                            }
                        })
                    }
                }
            } finally {
                lock.synchronize {
                    val l = waiter
                    waiter = null
                    l
                }?.resume(CloseReason.CHANNEL_CLOSED)
            }
        }
        val job2 = GlobalScope.launch(ctx, start = CoroutineStart.LAZY) {
            try {
                frame.useAsync { frame ->
                    stream.useAsync { stream ->
                        streamWrite = frame.copyTo(stream, bufferHook = {
                            it.holdState {
                                streamCrc.update(it)
                            }
//                            it.holdState {
//                                streamData.write(it)
//                            }
                        })
                    }
                }
            } finally {
                lock.synchronize {
                    val l = waiter
                    waiter = null
                    l
                }?.resume(CloseReason.FRAME_CLOSED)
            }
        }
        val r = suspendCancellableCoroutine<CloseReason> {
            waiter = it
            job1.start()
            job2.start()
        }
        job1.cancelAndJoin()
        job1.cancelAndJoin()
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
    bufferHook: ((ByteBuffer) -> Unit)? = null
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
    bufferHook: ((ByteBuffer) -> Unit)? = null
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
