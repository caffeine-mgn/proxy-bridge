package pw.binom.proxy.dto

import kotlinx.serialization.Serializable
import pw.binom.proxy.ChannelId
import pw.binom.validate.annotations.OneOf

@OneOf(
    "chanelEof",
    "proxyConnected",
    "proxyError",
    "channelEmmitError",
)
@Serializable
data class ControlEventDto(
    val chanelEof: ChanelEof? = null,
    val proxyConnected: ProxyConnected? = null,
    val proxyError: ProxyError? = null,
    val channelEmmitError: ChannelEmmitError? = null,
) {

    @Serializable
    data class ChannelEmmitError(
        val channelId: ChannelId,
        val msg: String?,
    )

    @Serializable
    data class ChanelEof(val channelId: ChannelId)

    @Serializable
    data class ProxyConnected(
        val channelId: ChannelId,
    )

    @Serializable
    data class ProxyError(
        val channelId: ChannelId,
        val msg: String?,
    )
}
