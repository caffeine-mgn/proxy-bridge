package pw.binom.http

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.io.Buffer
import kotlinx.io.Sink

@OptIn(InternalAPI::class)
class ChunkedByteWriteChannel(private val other: ByteWriteChannel) : ByteWriteChannel {
    companion object {
        private const val CrLfShort: Short = 0x0d0a
        private val CrLf = "\r\n".toByteArray()
        private val LastChunkBytes = "0\r\n\r\n".toByteArray()
    }

    override val isClosedForWrite: Boolean
        get() = other.isClosedForWrite
    override val closedCause: Throwable?
        get() = other.closedCause

    private val buffer = Buffer()

    @InternalAPI
    override val writeBuffer: Sink
        get() = buffer

    private suspend fun internalFlush() {
        other.writeFully(buffer.size.toString(16).encodeToByteArray())
        other.writeShort(CrLfShort)
        other.writeBuffer.write(buffer, buffer.size)
        other.writeFully(CrLf)
        buffer.clear()
    }


    override suspend fun flush() {
        if (buffer.exhausted()) {
            return
        }
        internalFlush()
        other.flush()
    }

    override suspend fun flushAndClose() {
        if (!buffer.exhausted()) {
            internalFlush()
        }
        other.writeFully(LastChunkBytes)
        other.flushAndClose()
    }

    override fun cancel(cause: Throwable?) {
        other.cancel(cause)
    }
}
