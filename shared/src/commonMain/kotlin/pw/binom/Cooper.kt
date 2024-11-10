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
import pw.binom.frame.FrameChannel
import pw.binom.io.AsyncChannel
import pw.binom.io.AsyncInput
import pw.binom.io.AsyncOutput
import pw.binom.io.ByteBuffer
import pw.binom.io.DataTransferSize
import pw.binom.io.use
import pw.binom.io.useAsync
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
        val job1 = GlobalScope.launch(ctx, start = CoroutineStart.LAZY) {
            try {
                frame.useAsync { frame ->
                    stream.useAsync { stream ->
                        frameWrite = stream.copyTo(frame)
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
                        streamWrite = frame.copyTo(stream)
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
        return ExchangeResult(
            reason = r,
            frameWrite = frameWrite,
            streamWrite = streamWrite,
        )
    }
}

suspend fun FrameChannel.copyTo(to: AsyncOutput): DataTransferSize {
    var size = 0
    ByteBuffer(this.bufferSize.asInt).use { buffer ->
        while (currentCoroutineContext().isActive) {
            val copyResult = readFrame { buf2 ->
                buffer.clear()
                buf2.readInto(buffer)
            }.valueOrNull ?: break
            if (copyResult > 0) {
                buffer.flip()
                try {
                    size += buffer.remaining
                    to.writeFully(buffer)
                    to.flush()
                } catch (_: SocketClosedException) {
                    break
                }
            }
        }
    }
    return DataTransferSize.ofSize(size)
}

suspend fun AsyncInput.copyTo(frameChannel: FrameChannel): DataTransferSize {
    println("AsyncInput.copyTo frameChannel=${frameChannel::class}")
    var size = 0
    ByteBuffer(frameChannel.bufferSize.asInt).use { buffer ->
        while (currentCoroutineContext().isActive) {
            buffer.clear()
            val l = try {
                read(buffer)
            } catch (e: SocketClosedException) {
                break
            }
            if (l.isAvailable) {
                buffer.flip()
                while (buffer.hasRemaining) {
                    val copyResult = frameChannel.sendFrame { frameOut ->
                        frameOut.writeFrom(buffer)
                    }.valueOrNull ?: break
                    size += copyResult
                }
            } else {
                break
            }
        }
    }
    return DataTransferSize.ofSize(size)
}
