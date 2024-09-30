package pw.binom

import pw.binom.io.AsyncChannel
import pw.binom.proxy.ChannelId
import pw.binom.proxy.channels.TransportChannel

object VirtualTransportChannel {
    fun create(id: ChannelId = ChannelId(0), func: suspend AsyncChannel.() -> Unit): TransportChannel =
        VirtualChannelImpl(
            channel = VirtualChannel.create(func),
            id = id
        )

    private class VirtualChannelImpl(
        private val channel: VirtualChannel,
        override val id: ChannelId
    ) : TransportChannel, AsyncChannel by channel {
        override fun toString(): String = "TestTransportChannel($id)"
        override var description: String? = null
        override val isClosed: Boolean
            get() = channel.isClosed
//        override suspend fun breakCurrentRole() {
//            TODO()
//        }
//
//        override suspend fun connectWith(other: AsyncChannel, bufferSize: Int): BridgeJob {
//            TODO()
//        }
    }
}
