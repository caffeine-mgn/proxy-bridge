package pw.binom.proxy

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
value class ChannelId(val raw: String) {
    constructor(id: Int) : this(id.toString())

    val id: Int
        get() = raw.toInt()
}
