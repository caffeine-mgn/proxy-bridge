package pw.binom

import pw.binom.io.AsyncChannel
import pw.binom.proxy.TransportChannelId
import pw.binom.proxy.channels.TransportChannel

object VirtualTransportChannel {
    fun create(id: TransportChannelId = TransportChannelId(""), func: suspend AsyncChannel.() -> Unit): TransportChannel =
        VirtualChannelImpl(
            channel = VirtualChannel.create(func),
            id = id
        )

    private class VirtualChannelImpl(
        private val channel: VirtualChannel,
        override val id: TransportChannelId
    ) : TransportChannel, AsyncChannel by channel {
        override fun toString(): String = "TestTransportChannel($id)"
        override var description: String? = null
        override val isClosed: Boolean
            get() = channel.isClosed
        override val input: Long
            get() = 0
        override val output: Long
            get() = 0
//        override suspend fun breakCurrentRole() {
//            TODO()
//        }
//
//        override suspend fun connectWith(other: AsyncChannel, bufferSize: Int): BridgeJob {
//            TODO()
//        }
    }
}
