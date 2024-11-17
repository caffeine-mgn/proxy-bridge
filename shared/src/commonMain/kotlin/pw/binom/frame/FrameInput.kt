package pw.binom.frame

import pw.binom.fromBytes
import pw.binom.io.ByteBuffer

interface FrameInput {
    fun readByte(): Byte
    fun readBoolean() = readByte() > 0
    fun readInt(): Int = Int.fromBytes(readByteArray(4))
    fun readLong(): Long = Long.fromBytes(readByteArray(8))
    fun readShort(): Short = Short.fromBytes(readByteArray(2))
    fun readByteArray(dest: ByteArray) {
        for (i in 0 until dest.size) {
            dest[i] = readByte()
        }
    }

    fun readString(): String {
        val len = readInt()
        return readByteArray(len).decodeToString()
    }

    fun readByteArray(size: Int): ByteArray {
        val d = ByteArray(size)
        readByteArray(d)
        return d
    }

    fun readInto(byteBuffer: ByteBuffer): Int {
        val counter = byteBuffer.remaining
        while (byteBuffer.hasRemaining) {
            byteBuffer.put(readByte())
        }
        return counter
    }
}
