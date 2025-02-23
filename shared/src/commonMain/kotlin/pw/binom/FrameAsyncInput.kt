package pw.binom

import kotlinx.coroutines.isActive
import pw.binom.io.AsyncInput
import pw.binom.io.Available
import pw.binom.io.ByteBuffer
import pw.binom.io.DataTransferSize
import kotlin.coroutines.coroutineContext

@Deprecated(message = "Not use it")
class FrameAsyncInput(val func: suspend () -> AsyncInput?) : AsyncInput {
    override val available: Available
        get() = Available.UNKNOWN

    private var buffer: AsyncInput? = null

    override suspend fun asyncClose() {
    }

    override suspend fun read(dest: ByteBuffer): DataTransferSize {
        while (coroutineContext.isActive) {
            var buffer = buffer
            if (buffer == null) {
                val newBuffer = func() ?: return DataTransferSize.EMPTY
                buffer = newBuffer
                this.buffer = buffer
            }
            val l = buffer.read(dest)
            if (l.isNotAvailable) {
                this.buffer = null
                continue
            }
            return l
        }
        return DataTransferSize.EMPTY
    }
}
