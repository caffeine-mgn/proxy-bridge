package pw.binom.frame

import pw.binom.*
import pw.binom.io.ByteBuffer

abstract class AbstractByteBufferFrameOutput : FrameOutput {
    protected abstract val buffer: ByteBuffer
    override fun writeByte(value: Byte) {
        buffer.put(value)
    }

    override fun writeFrom(src: ByteBuffer) = buffer.write(src).length

    override fun writeInt(value: Int) {
        buffer.writeInt(value)
    }

    override fun writeLong(value: Long) {
        buffer.writeLong(value)
    }

    override fun writeShort(value: Short) {
        buffer.writeShort(value)
    }

    override fun writeByteArray(data: ByteArray): Int {
        buffer.write(data)
        return data.size
    }
}
