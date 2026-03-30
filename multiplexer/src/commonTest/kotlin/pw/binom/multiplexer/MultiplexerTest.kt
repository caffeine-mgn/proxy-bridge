package pw.binom.multiplexer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

class MultiplexerTest {

    class Stub : AutoCloseable {
        val input = Channel<Buffer>(Channel.UNLIMITED)
        val output = Channel<Buffer>(Channel.UNLIMITED)
        val events = MultiplexerProtocol.readEvent(output)
        override fun close() {
            input.close()
            output.close()
            events.cancel()
        }
    }

    lateinit var stub: Stub
    lateinit var multiplexer: Multiplexer

    @BeforeTest
    fun setup() {
        stub = Stub()
        multiplexer = Multiplexer(
            input = stub.input,
            output = stub.output,
            idOdd = true,
            ioCoroutineScope = CoroutineScope(Dispatchers.Default),
        )
    }

    @AfterTest
    fun shutdown() {
        stub.close()
        multiplexer.close()
    }

    @Test
    fun testAccept() = runTest {
        MultiplexerProtocol.sendRequestNewChannel(channelId = 111, physical = stub.input)
        val accepted = coroutineScope {
            launch {
                multiplexer.accept()
            }
        }
        accepted.join()
    }

    @Test
    fun testNewChannel() = runTest(timeout = 5.seconds) {
        val createChannelJob = CoroutineScope(Dispatchers.Unconfined).launch {
            multiplexer.createChannel()
        }
        val event = stub.events.receive() as MultiplexerEvent.ChannelRequest
        MultiplexerProtocol.sendResponseNewChannel(event.channelId, stub.input)
        createChannelJob.join()
    }

    @Test
    fun testReceive() = runTest(timeout = 5.seconds) {
        val data = Random.nextBytes(500)
        MultiplexerProtocol.sendRequestNewChannel(channelId = 111, physical = stub.input)
        stub.input.send(MultiplexerProtocol.wrapLogicalToPhysical(channelId = 111, data = bufferOf(data)))

        val channel = multiplexer.accept()
        assertContentEquals(data, channel.income.receive().readByteArray())
    }

    @Test
    fun testSend() = runTest {
        val channelId = 111
        val data = Random.nextBytes(500)
        MultiplexerProtocol.sendRequestNewChannel(channelId = channelId, physical = stub.input)
        val channel = multiplexer.accept()
        channel.outcome.send(bufferOf(data))
        stub.events.receive() as MultiplexerEvent.NewChannelAccepted
        val channelData = stub.events.receive() as MultiplexerEvent.ChannelData
        assertEquals(channelId, channelData.channelId)
        assertContentEquals(data, channelData.data.readByteArray())
    }

    @Test
    fun testCloseOutside() = runTest(timeout = 2.seconds) {
        val channelId = 111
        MultiplexerProtocol.sendRequestNewChannel(channelId = channelId, physical = stub.input)
        val channel = multiplexer.accept()
        MultiplexerProtocol.sendCloseChannel(channelId = channelId, physical = stub.input)
        try {
            channel.income.receive()
            fail()
        } catch (e: CancellationException) {
            // ignore
        }
        try {
            channel.outcome.send(bufferOf(1))
            fail()
        } catch (e: CancellationException) {
            // ignore
        }
    }

    @Test
    fun testCloseInside() = runTest(timeout = 5.seconds) {
        val channelId = 111
        MultiplexerProtocol.sendRequestNewChannel(channelId = channelId, physical = stub.input)
        val channel = multiplexer.accept()
        channel.income.cancel()
        stub.events.receive() as MultiplexerEvent.NewChannelAccepted
        val closeChannel = stub.events.receive() as MultiplexerEvent.ChannelClosed
        assertEquals(channelId, closeChannel.channelId)
        delay(1.seconds)
        val otherEvents = withTimeoutOrNull(2.seconds) {
            stub.events.receive()
        }
        assertNull(otherEvents)
        println("-->$otherEvents")
    }
}
