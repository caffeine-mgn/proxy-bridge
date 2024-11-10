package pw.binom.frame

import pw.binom.io.ByteBuffer
import pw.binom.readInt
import pw.binom.readShort

abstract class AbstractByteBufferFrameInput : FrameInput {
    protected abstract val buffer: ByteBuffer

    override fun readByte(): Byte = buffer.getByte()

    override fun readInto(byteBuffer: ByteBuffer) = byteBuffer.write(buffer).length

    override fun readShort(): Short = buffer.readShort()

    override fun readInt(): Int =
        buffer.readInt()

    override fun readByteArray(dest: ByteArray) {
        buffer.readInto(dest)
    }
}
