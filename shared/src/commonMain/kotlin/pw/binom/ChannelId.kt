package pw.binom

import pw.binom.frame.FrameInput
import pw.binom.frame.FrameOutput
import pw.binom.io.ByteBuffer
import kotlin.jvm.JvmInline

@JvmInline
value class ChannelId(val raw: Short) {
    companion object {
        const val SIZE_BYTES = Short.SIZE_BYTES
        fun read(buf: ByteBuffer): ChannelId = ChannelId(buf.readShort())
        fun read(buf: FrameInput): ChannelId = ChannelId(buf.readShort())
    }

    inline val toUShort: UShort
        get() = raw.toUShort()

    fun write(buffer: FrameOutput) {
        buffer.writeShort(raw)
    }

    fun write(buffer: ByteBuffer) {
        buffer.writeShort(raw)
    }
}
