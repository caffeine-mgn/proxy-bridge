package pw.binom.proxy

import kotlinx.coroutines.CancellableContinuation
import pw.binom.io.AsyncChannel
import pw.binom.io.ByteBuffer
import kotlin.coroutines.resume

class RemoteAsyncChannel(val channel: AsyncChannel, val continuation: CancellableContinuation<Unit>) : AsyncChannel {
    override val available: Int
        get() = channel.available

    override suspend fun asyncClose() {
        continuation.resume(Unit)
        channel.asyncCloseAnyway()
    }

    override suspend fun flush() {
        channel.flush()
    }

    override suspend fun read(dest: ByteBuffer): Int =
        try {
            channel.read(dest)
        } catch (e: Throwable) {
            asyncCloseAnyway()
            throw e
        }

    override suspend fun write(data: ByteBuffer): Int =
        try {
            channel.writeFully(data)
        } catch (e: Throwable) {
            asyncCloseAnyway()
            throw e
        }
}