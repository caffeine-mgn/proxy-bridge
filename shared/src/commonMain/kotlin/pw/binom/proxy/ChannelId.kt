package pw.binom.proxy

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
value class ChannelId(val id: String) {
    val asString
        get() = id.toString()
}
