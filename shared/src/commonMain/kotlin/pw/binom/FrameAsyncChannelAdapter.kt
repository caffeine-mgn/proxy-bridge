package pw.binom

import pw.binom.atomic.AtomicBoolean
import pw.binom.frame.FrameChannel
import pw.binom.io.AsyncChannel
import pw.binom.io.ByteBuffer
import pw.binom.io.DataTransferSize
import pw.binom.io.empty

class FrameAsyncChannelAdapter(val channel: FrameChannel) : AsyncChannel {

    private val buffer = byteBuffer(channel.bufferSize.asInt)
    private val closed = AtomicBoolean(false)

    override val available: Int
        get() = -1

    init {
        buffer.empty()
    }

    override suspend fun read(dest: ByteBuffer): DataTransferSize {
        if (!dest.hasRemaining) {
            return DataTransferSize.EMPTY
        }
        while (true) {
            if (buffer.hasRemaining) {
                return dest.write(buffer)
            }
            val r = channel.readFrame { buf ->
                buffer.clear()
                val readLen = buf.readInto(buffer)
                buffer.flip()
            }
            if (r.isClosed) {
                return DataTransferSize.CLOSED
            }
        }
    }

    override suspend fun write(data: ByteBuffer): DataTransferSize {
        var result = 0
        while (data.hasRemaining) {
            val sendResult = channel.sendFrame { buf ->
                buf.writeFrom(data)
            }
            if (sendResult.isClosed) {
                if (result == 0) {
                    DataTransferSize.CLOSED
                }
                return DataTransferSize.ofSize(result)
            }
            val sent = sendResult.getOrThrow()
            if (sent == 0) {
                return DataTransferSize.ofSize(result)
            }
            result += sent
        }
        return DataTransferSize.ofSize(result)
    }

    override suspend fun flush() {
        // Do nothing
    }

    override suspend fun asyncClose() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        buffer.close()
        channel.asyncClose()
    }
}
