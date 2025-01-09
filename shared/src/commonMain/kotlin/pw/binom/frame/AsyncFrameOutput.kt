package pw.binom.frame

import pw.binom.atomic.AtomicBoolean
import pw.binom.io.AsyncOutput
import pw.binom.io.ByteBuffer
import pw.binom.io.DataTransferSize

class AsyncFrameOutput(val output: FrameSender) : AsyncOutput {
    private val closed = AtomicBoolean(false)
    private val buffer = ByteBuffer(output.bufferSize.asInt)
    override suspend fun asyncClose() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        try {
            flush()
            output.asyncClose()
        } finally {
            buffer.close()
        }
    }

    override suspend fun flush() {
        if (closed.getValue()) {
            return
        }
        if (buffer.position == 0) {
            return
        }
        buffer.flip()
        while (buffer.hasRemaining) {
            output.sendFrame {
                it.writeFrom(buffer)
            }
        }
        buffer.clear()
    }

    override suspend fun write(data: ByteBuffer): DataTransferSize {
        if (closed.getValue()) {
            return DataTransferSize.CLOSED
        }
        val l = buffer.write(data)
        if (!buffer.hasRemaining) {
            flush()
        }
        return l
    }
}
