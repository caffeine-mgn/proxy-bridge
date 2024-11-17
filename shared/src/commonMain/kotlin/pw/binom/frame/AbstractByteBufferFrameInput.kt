package pw.binom.frame

import pw.binom.*
import pw.binom.io.ByteBuffer

abstract class AbstractByteBufferFrameInput : FrameInput {
    protected abstract val buffer: ByteBuffer

    override fun readByte(): Byte = buffer.getByte()

    override fun readInto(byteBuffer: ByteBuffer) = byteBuffer.write(data = buffer).length

    override fun readShort(): Short = buffer.readShort()

    override fun readInt(): Int = buffer.readInt()

    override fun readLong(): Long = buffer.readLong()

    override fun readByteArray(dest: ByteArray) {
        buffer.readInto(dest)
    }
}
