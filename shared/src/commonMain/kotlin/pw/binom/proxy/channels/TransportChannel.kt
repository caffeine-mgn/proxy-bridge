package pw.binom.proxy.channels

import pw.binom.io.AsyncChannel
import pw.binom.proxy.TransportChannelId

interface TransportChannel : AsyncChannel {
    val id: TransportChannelId
    var description: String?
    val isClosed: Boolean
    val input: Long
    val output: Long
}
