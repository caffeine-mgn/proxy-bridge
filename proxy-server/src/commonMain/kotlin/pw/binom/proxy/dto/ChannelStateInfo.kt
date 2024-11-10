package pw.binom.proxy.dto

import kotlinx.serialization.Serializable
import pw.binom.proxy.TransportChannelId

@Serializable
class ChannelStateInfo(
    val id: TransportChannelId,
    val behavior: String?,
    val input:Long,
    val output:Long,
)
