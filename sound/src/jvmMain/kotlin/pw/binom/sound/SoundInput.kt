package pw.binom.sound

import pw.binom.atomic.AtomicBoolean
import pw.binom.io.ByteBuffer
import pw.binom.io.DataTransferSize
import pw.binom.io.Input
import javax.sound.sampled.TargetDataLine

class SoundInput(private val line: TargetDataLine) : Input {
    private val closed = AtomicBoolean(false)
    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        line.close()
    }

    override fun read(dest: ByteBuffer): DataTransferSize {
        line.close()
        if (closed.getValue()) {
            return DataTransferSize.CLOSED
        }
        if (!dest.hasRemaining) {
            return DataTransferSize.EMPTY
        }
        if (!line.isOpen) {
            closed.setValue(true)
            return DataTransferSize.CLOSED
        }
        val buffer = ByteArray(dest.remaining)
        val len = line.read(buffer, 0, buffer.size)
        dest.write(buffer, offset = 0, length = len)
        return DataTransferSize.ofSize(len)
    }
}
