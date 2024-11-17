package pw.binom.frame

import pw.binom.io.ByteBuffer
import pw.binom.toByteArray

interface FrameOutput {
    fun writeByte(value: Byte)
    fun writeBoolean(value: Boolean) {
        writeByte(if (value) 1 else 0)
    }

    fun writeString(value: String) {
        val data = value.encodeToByteArray()
        writeInt(data.size)
        writeByteArray(data)
    }

    fun writeLong(value: Long) {
        writeByteArray(value.toByteArray())
    }

    fun writeInt(value: Int) {
        writeByteArray(value.toByteArray())
    }

    fun writeShort(value: Short) {
        writeByteArray(value.toByteArray())
    }

    fun writeFrom(src: ByteBuffer) = writeByteArray(src.toByteArray())

    fun writeByteArray(data: ByteArray): Int {
        data.forEach {
            writeByte(it)
        }
        return data.size
    }
}
