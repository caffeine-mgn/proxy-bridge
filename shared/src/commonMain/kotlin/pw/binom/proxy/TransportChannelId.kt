package pw.binom.proxy

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Идентификатор транспортного канала
 */
@JvmInline
@Serializable
value class TransportChannelId(val id: String) {
    val asString
        get() = id.toString()
}
