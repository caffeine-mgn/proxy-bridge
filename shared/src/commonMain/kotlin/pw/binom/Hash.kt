package pw.binom

import kotlin.jvm.JvmInline

@JvmInline
value class Hash(val raw: ByteArray) {
    @OptIn(ExperimentalStdlibApi::class)
    override fun toString(): String = raw.toHexString()
}
