package pw.binom.proxy.client

import pw.binom.io.AsyncChannel
import pw.binom.strong.inject

class ConnectionPool {
    private val connectionFactory by inject<ConnectionFactory>()
    private val channels = HashMap<Int, InternalChannel>()

    private fun returnChannel(channel: InternalChannel) {
        channels[channel.id] = channel
    }

    inner class InternalChannel(
        val id: Int,
        val channel: AsyncChannel,
        var channelFinished: ChannelFinished?
    ) : AsyncChannel by channel {
        override suspend fun asyncClose() {
            channelFinished?.finished(id)
            returnChannel(this)
        }

        suspend fun forceClose() {
            channel.asyncCloseAnyway()
        }
    }

    interface ChannelFinished {
        fun finished(id: Int)
    }

    suspend fun getOrCreate(id: Int, channelFinished: ChannelFinished): InternalChannel {
        var exist = channels.remove(id)
        if (exist == null) {
            exist = InternalChannel(id = id, channel = connectionFactory.connect(id), channelFinished = channelFinished)
        } else {
            exist.channelFinished = channelFinished
        }
        return exist
    }

    suspend fun foreClosed(channel: InternalChannel) {
        channel.forceClose()
    }
}
