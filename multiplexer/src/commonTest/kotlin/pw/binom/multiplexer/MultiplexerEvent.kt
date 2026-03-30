package pw.binom.multiplexer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.io.Buffer

fun MultiplexerProtocol.readEvent(physical: ReceiveChannel<Buffer>): Channel<MultiplexerEvent> {
    val channel = Channel<MultiplexerEvent>(Channel.UNLIMITED)

    CoroutineScope(Dispatchers.Default).launch {
        val currentJob = coroutineContext.job
        channel.invokeOnClose {
            println("Cancelling")
            currentJob.cancel()
        }
        reading(
            physical,
            handlerOnData = { a, b ->
                channel.send(MultiplexerEvent.ChannelData(a, b))
            },
            channelClosed = {
                channel.send(MultiplexerEvent.ChannelClosed(it))
            },
            requestChannel = {
                channel.send(MultiplexerEvent.ChannelRequest(it))
            },
            newChannelAccepted = {
                channel.send(MultiplexerEvent.NewChannelAccepted(it))
            },
        )
    }
    return channel
}

sealed interface MultiplexerEvent {
    data class ChannelData(val channelId: Int, val data: Buffer) : MultiplexerEvent
    data class ChannelClosed(val channelId: Int) : MultiplexerEvent
    data class ChannelRequest(val channelId: Int) : MultiplexerEvent
    data class NewChannelAccepted(val channelId: Int) : MultiplexerEvent
}
