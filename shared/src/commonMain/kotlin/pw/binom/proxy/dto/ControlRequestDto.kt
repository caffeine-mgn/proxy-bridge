package pw.binom.proxy.dto

import kotlinx.serialization.Serializable
import pw.binom.proxy.ChannelId
import pw.binom.TransportType
import pw.binom.validate.annotations.OneOf

@Serializable
@OneOf(
    "emmitChannel",
    "changeChannelRole",
    "resetChannel",
    "closeChannel",
)
data class ControlRequestDto(
    val emmitChannel: EmmitChannel? = null,
    val proxyConnect: ProxyConnect? = null,
    val resetChannel: ResetChannel? = null,
    val closeChannel: CloseChannel? = null,
) {
    @Serializable
    data class EmmitChannel(val id: ChannelId, val type: TransportType)

    @Serializable
    data class CloseChannel(val id: ChannelId)

    @Serializable
    data class ProxyConnect(
        val id: ChannelId,
        val host: String,
        val port: Int,
    )

    @Serializable
    data class ResetChannel(
        val id: ChannelId,
    )
}
