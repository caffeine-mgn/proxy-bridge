package pw.binom.proxy.dto

import kotlinx.serialization.Serializable
import pw.binom.frame.PackageSize
import pw.binom.proxy.TransportChannelId
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
//    val resetChannel: ResetChannel? = null,
    val closeChannel: CloseChannel? = null,
) {
    @Serializable
    data class EmmitChannel(
        val id: TransportChannelId,
        val type: TransportType,
        val bufferSize: PackageSize,
    )

    @Serializable
    data class CloseChannel(val id: TransportChannelId)

    @Serializable
    data class ProxyConnect(
        val id: TransportChannelId,
        val host: String,
        val port: Int,
        val compressLevel: Int,
    )

//    @Serializable
//    data class ResetChannel(
//        val id: ChannelId,
//    )
}
