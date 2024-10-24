package pw.binom.proxy.dto

import kotlinx.serialization.Serializable
import pw.binom.proxy.ChannelId

@Serializable
class ChannelStateInfo(
    val id: ChannelId,
    val behavior: String?,
    val input:Long,
    val output:Long,
)
