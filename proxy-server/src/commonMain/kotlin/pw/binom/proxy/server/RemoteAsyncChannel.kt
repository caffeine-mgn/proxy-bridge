package pw.binom.proxy.server

import kotlinx.coroutines.CancellableContinuation
import pw.binom.io.AsyncChannel
import pw.binom.io.ByteBuffer
import pw.binom.io.DataTransferSize
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

    override suspend fun read(dest: ByteBuffer): DataTransferSize =
        try {
            channel.read(dest)
        } catch (e: Throwable) {
            asyncCloseAnyway()
            throw e
        }

    override suspend fun write(data: ByteBuffer): DataTransferSize =
        if (data.hasRemaining){
            try {
                DataTransferSize.ofSize(channel.writeFully(data))
            } catch (e: Throwable) {
                asyncCloseAnyway()
                throw e
            }
        } else {
            DataTransferSize.EMPTY
        }

}
