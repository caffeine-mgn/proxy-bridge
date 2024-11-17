package pw.binom

import kotlin.jvm.JvmInline

@JvmInline
value class ChannelId(val raw: Short) {
    companion object {
        const val SIZE_BYTES = Short.SIZE_BYTES
    }

    inline val toUShort
        get() = raw.toUShort()
}
