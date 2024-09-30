package pw.binom.proxy.channels

import pw.binom.io.AsyncChannel
import pw.binom.proxy.BridgeJob
import pw.binom.proxy.ChannelId

interface TransportChannel : AsyncChannel {
    val id: ChannelId
    var description: String?
    val isClosed: Boolean
}
