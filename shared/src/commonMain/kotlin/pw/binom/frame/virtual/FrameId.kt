package pw.binom.frame.virtual

import pw.binom.frame.FrameInput
import pw.binom.frame.FrameReceiver
import kotlin.jvm.JvmInline

@JvmInline
value class FrameId(val raw: Byte) {
    companion object {
        val SIZE_IN_BYTE = Byte.SIZE_BYTES
        val INIT = FrameId(0)
        fun read(input: FrameInput) = FrameId(input.readByte())
        suspend fun read(input: FrameReceiver) = input.readFrame { read(it) }
    }

    inline val isInit
        get() = raw == INIT.raw

    inline val asByte
        get() = raw

    override fun toString(): String = "FrameId($asShortString)"
    val asShortString
        get() = raw.toUByte().toString()

    fun isNext(other: FrameId): Boolean {
        if (raw == Byte.MAX_VALUE && other.raw == Byte.MIN_VALUE) {
            return true
        }
        return other.raw - raw == 1
    }

    val next: FrameId
        get() {
            var a = raw
            return FrameId(++a)
        }

    val previous: FrameId
        get() {
            var a = raw
            return FrameId(--a)
        }
}
