package pw.binom.proxy

import kotlinx.serialization.Serializable

@Serializable
class RequestDto(
    val connect: Connect? = null,
    val emmitChannel: EmmitChannel? = null,
    val destroyChannel: DestroyChannel? = null,
    val setProxy: SetProxy? = null,
    val setIdle: SetIdle? = null,
) {
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
