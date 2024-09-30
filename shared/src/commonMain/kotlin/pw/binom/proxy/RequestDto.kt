package pw.binom.proxy

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import pw.binom.ArgCounter
import pw.binom.proxy.serialization.ShortSerialization

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RequestDto(
    val connect: Connect? = null,
    val emmitChannel: EmmitChannel? = null,
    val destroyChannel: DestroyChannel? = null,
    val setProxy: SetProxy? = null,
    val setIdle: SetIdle? = null,
) {
    companion object {
        fun fromByteArray(array: ByteArray) = ShortSerialization.decodeByteArray(serializer(), array)
    }

    fun toByteArray() =
        ShortSerialization.encodeByteArray(
            serializer(),
            this
        )

    init {
        ArgCounter.calc {
            add(connect, "connect")
            add(emmitChannel, "emmitChannel")
            add(destroyChannel, "destroyChannel")
            add(setProxy, "setProxy")
            add(setIdle, "setIdle")
        }
    }

    @Serializable
    data class Connect(val host: String, val port: Int, val channelId: Int)

    @Serializable
    data class EmmitChannel(val channelId: Int)

    @Serializable
    data class DestroyChannel(val channelId: Int)

    @Serializable
    data class SetProxy(val channelId: Int, val host: String, val port: Int)

    @Serializable
    data class SetIdle(val channelId: Int)
}
