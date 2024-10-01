package pw.binom.proxy.server.dto

import kotlinx.serialization.Serializable
import pw.binom.proxy.ChannelId

@Serializable
class ChannelStateInfo(
    val id: ChannelId,
    val behavior: String?,
)
