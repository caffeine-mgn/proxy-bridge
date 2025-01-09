package pw.binom.frame

import pw.binom.atomic.AtomicBoolean
import pw.binom.io.AsyncInput
import pw.binom.io.ByteBuffer
import pw.binom.io.DataTransferSize
import pw.binom.io.empty

class AsyncFrameInput(val input: FrameReceiver) : AsyncInput {
    private val closed = AtomicBoolean(false)
    private val buffer = ByteBuffer(input.bufferSize.asInt).empty()

    override val available: Int
        get() = when {
            closed.getValue() -> 0
            buffer.hasRemaining -> buffer.remaining
            else -> -1
        }

    override suspend fun asyncClose() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        try {
            input.asyncClose()
        } finally {
            buffer.close()
        }
    }

    override suspend fun read(dest: ByteBuffer): DataTransferSize {
        if (closed.getValue()) {
            return DataTransferSize.CLOSED
        }
        if (!buffer.hasRemaining) {
            while (true) {
                buffer.clear()
                val e = input.readFrame { it.readInto(buffer) }.valueOrNull ?: return DataTransferSize.CLOSED
                if (e > 0) {
                    buffer.flip()
                    break
                }
            }
        }
        return DataTransferSize.ofSize(buffer.readInto(dest))
    }
}
