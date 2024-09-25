package pw.binom.proxy.dto

import kotlinx.serialization.Serializable
import pw.binom.proxy.ChannelId
import pw.binom.proxy.ChannelRole
import pw.binom.proxy.TransportType
import pw.binom.proxy.serialization.URLSerializer
import pw.binom.url.URL
import pw.binom.validate.annotations.OneOf

@Serializable
@OneOf(
    "emmitChannel",
    "changeChannelRole",
    "resetChannel",
)
data class ControlRequestDto(
    val emmitChannel: EmmitChannel? = null,
    val proxyConnect: ProxyConnect? = null,
    val resetChannel: ResetChannel? = null,
) {
    @Serializable
    data class EmmitChannel(val id: ChannelId, val type: TransportType)

    @Serializable
    data class ProxyConnect(
        val id: ChannelId,
        val host: String,
        val port: Short,
    )

    @Serializable
    data class ResetChannel(
        val id: ChannelId,
    )
}
