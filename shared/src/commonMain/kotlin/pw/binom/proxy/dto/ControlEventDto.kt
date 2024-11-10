package pw.binom.proxy.dto

import kotlinx.serialization.Serializable
import pw.binom.proxy.TransportChannelId
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
        val channelId: TransportChannelId,
        val msg: String?,
    )

    @Serializable
    data class ChanelEof(val channelId: TransportChannelId)

    @Serializable
    data class ProxyConnected(
        val channelId: TransportChannelId,
    )

    @Serializable
    data class ProxyError(
        val channelId: TransportChannelId,
        val msg: String?,
    )
}
