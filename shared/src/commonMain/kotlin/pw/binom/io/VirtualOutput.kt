package pw.binom.io

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.io.Buffer
import pw.binom.frame.PackageSize

class VirtualOutput(
    private val channel: SendChannel<Buffer>,
    private var maxBufferSize: PackageSize,
) : ChannelOut {
    private var buffer = Buffer()

    override suspend fun write(source: Buffer, byteCount: Long) {
        if (channel.isClosedForSend) {
            throw IllegalStateException("Channel closed")
        }
        var remaining = byteCount
        while (remaining > 0) {
            val wasRead = source.readAtMostTo(buffer, minOf(remaining, maxBufferSize.asLong - buffer.size))
            if (wasRead == -1L) {
                break
            }
            remaining -= wasRead
            if (buffer.size == maxBufferSize.asLong) {
                flush()
            }
        }
    }

    override suspend fun writeByte(value: Byte) {
        buffer.writeByte(value)
    }

    override suspend fun writeShort(value: Short) {
        buffer.writeShort(value)
    }

    override suspend fun flush() {
        if (buffer.size <= 0) {
            return
        }
        val buff = buffer
        buffer = Buffer()
        channel.send(buff)
    }

    override fun close() {
        channel.close(CancellationException("Closed by method"))
    }
}
