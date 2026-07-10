package pw.binom.multiplexer

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.io.Buffer
import kotlin.test.*

class MultiplexerCyclesTest {

    @OptIn(DelicateCoroutinesApi::class)
    private fun runTest(timeoutMs: Long, block: suspend CoroutineScope.() -> Unit) {
        val d = newSingleThreadContext("test")
        try {
            runBlocking(d) { withTimeout(timeoutMs) { block() } }
        } finally { d.close() }
    }

    @Test
    fun test10Cycles() {
        runTest(10_000) {
            val input = Channel<Buffer>(Channel.UNLIMITED)
            val output = Channel<Buffer>(Channel.UNLIMITED)
            val events = MultiplexerProtocol.readEvent(output)
            val multiplexer = MultiplexerImpl(
                input = input, output = output, idOdd = true,
                ioCoroutineScope = CoroutineScope(Dispatchers.Default),
            )

            repeat(10) {
                val d = CompletableDeferred<DuplexChannel>()
                launch(Dispatchers.Unconfined) { d.complete(multiplexer.createChannel()) }
                val request = events.receive() as MultiplexerEvent.ChannelRequest
                MultiplexerProtocol.sendResponseNewChannel(request.channelId, input)
                val channel = d.await()
                channel.close()
                events.receive() as MultiplexerEvent.ChannelClosed
            }

            multiplexer.close()
            input.close()
            output.close()
            events.cancel()
        }
    }
}
