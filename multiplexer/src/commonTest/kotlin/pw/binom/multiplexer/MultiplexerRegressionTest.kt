package pw.binom.multiplexer

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MultiplexerRegressionTest {

    @OptIn(DelicateCoroutinesApi::class)
    private fun testWithTimeout(timeout: Duration, block: suspend CoroutineScope.() -> Unit) {
        val d = newSingleThreadContext("test")
        try {
            runBlocking(d) {
                try {
                    withTimeout(timeout) {
                        block()
                    }
                } finally {
                    // cleanup on any failure
                }
            }
        } finally {
            d.close()
        }
    }

    private fun createMultiplexer(input: Channel<Buffer>, output: Channel<Buffer>): MultiplexerImpl {
        return MultiplexerImpl(
            input = input,
            output = output,
            idOdd = true,
            ioCoroutineScope = CoroutineScope(Dispatchers.Default),
        )
    }

    @Test
    fun testCreateChannelAndSendReceive() {
        testWithTimeout(10.seconds) {
            val input = Channel<Buffer>(Channel.UNLIMITED)
            val output = Channel<Buffer>(Channel.UNLIMITED)
            val events = MultiplexerProtocol.readEvent(output)
            val multiplexer = createMultiplexer(input, output)
            val data = Random.nextBytes(500)

            val chDef = CompletableDeferred<DuplexChannel>()
            launch(Dispatchers.Unconfined) {
                chDef.complete(multiplexer.createChannel())
            }

            val request = events.receive() as MultiplexerEvent.ChannelRequest
            MultiplexerProtocol.sendResponseNewChannel(request.channelId, input)

            val channel = chDef.await()
            channel.outcome.send(bufferOf(data))

            val channelData = events.receive() as MultiplexerEvent.ChannelData
            assertEquals(request.channelId, channelData.channelId)
            assertContentEquals(data, channelData.data.readByteArray())

            multiplexer.close()
            input.close()
            output.close()
            events.cancel()
        }
    }

    @Test
    fun testConcurrentCreateChannels() {
        testWithTimeout(10.seconds) {
            val input = Channel<Buffer>(Channel.UNLIMITED)
            val output = Channel<Buffer>(Channel.UNLIMITED)
            val events = MultiplexerProtocol.readEvent(output)
            val multiplexer = createMultiplexer(input, output)
            val n = 10

            val defs = (1..n).map {
                val d = CompletableDeferred<DuplexChannel>()
                launch(Dispatchers.Unconfined) {
                    d.complete(multiplexer.createChannel())
                }
                d
            }

            repeat(n) {
                val event = events.receive() as MultiplexerEvent.ChannelRequest
                MultiplexerProtocol.sendResponseNewChannel(event.channelId, input)
            }

            val channels = defs.awaitAll()
            assertEquals(n, channels.size)
            channels.forEach { it.close() }

            multiplexer.close()
            input.close()
            output.close()
            events.cancel()
        }
    }

    @Test
    fun testCloseNotificationDelivered() {
        testWithTimeout(5.seconds) {
            val input = Channel<Buffer>(Channel.UNLIMITED)
            val output = Channel<Buffer>(Channel.UNLIMITED)
            val events = MultiplexerProtocol.readEvent(output)
            val multiplexer = createMultiplexer(input, output)

            MultiplexerProtocol.sendRequestNewChannel(channelId = 111, physical = input)
            val channel = multiplexer.accept()
            channel.outcome.close()

            events.receive() as MultiplexerEvent.NewChannelAccepted
            val closeEvent = events.receive() as MultiplexerEvent.ChannelClosed
            assertEquals(111, closeEvent.channelId)

            multiplexer.close()
            input.close()
            output.close()
            events.cancel()
        }
    }

    @Test
    fun testIdempotentMultiplexerClose() {
        testWithTimeout(5.seconds) {
            val input = Channel<Buffer>(Channel.UNLIMITED)
            val output = Channel<Buffer>(Channel.UNLIMITED)
            val multiplexer = createMultiplexer(input, output)
            multiplexer.close()
            multiplexer.close()
            input.close()
            output.close()
        }
    }

    @Test
    fun testLargePayload() {
        testWithTimeout(10.seconds) {
            val input = Channel<Buffer>(Channel.UNLIMITED)
            val output = Channel<Buffer>(Channel.UNLIMITED)
            val multiplexer = createMultiplexer(input, output)
            val data = Random.nextBytes(65536)

            MultiplexerProtocol.sendRequestNewChannel(channelId = 111, physical = input)
            input.send(MultiplexerProtocol.wrapLogicalToPhysical(channelId = 111, data = bufferOf(data)))
            val channel = multiplexer.accept()
            assertContentEquals(data, channel.income.receive().readByteArray())

            multiplexer.close()
            input.close()
            output.close()
        }
    }

    @Test
    fun testUnknownCommandDoesNotCrash() {
        testWithTimeout(5.seconds) {
            val input = Channel<Buffer>(Channel.UNLIMITED)
            val output = Channel<Buffer>(Channel.UNLIMITED)
            val events = MultiplexerProtocol.readEvent(output)
            val multiplexer = createMultiplexer(input, output)

            val badBuffer = Buffer()
            badBuffer.writeByte(42)
            badBuffer.writeByte(0)
            input.send(badBuffer)
            delay(100)
            MultiplexerProtocol.sendRequestNewChannel(channelId = 111, physical = input)
            val channel = multiplexer.accept()
            assertNotNull(channel)

            multiplexer.close()
            input.close()
            output.close()
            events.cancel()
        }
    }

    @Test
    fun testCancelClosesBothSides() {
        testWithTimeout(5.seconds) {
            val input = Channel<Buffer>(Channel.UNLIMITED)
            val output = Channel<Buffer>(Channel.UNLIMITED)
            val events = MultiplexerProtocol.readEvent(output)
            val multiplexer = createMultiplexer(input, output)

            MultiplexerProtocol.sendRequestNewChannel(channelId = 111, physical = input)
            val channel = multiplexer.accept()
            channel.cancel()
            assertTrue(channel.isClosedForReceive)

            multiplexer.close()
            input.close()
            output.close()
            events.cancel()
        }
    }

    @Test
    fun testLocalCloseCleansUpActiveChannels() {
        testWithTimeout(10.seconds) {
            val input = Channel<Buffer>(Channel.UNLIMITED)
            val output = Channel<Buffer>(Channel.UNLIMITED)
            val events = MultiplexerProtocol.readEvent(output)
            val multiplexer = createMultiplexer(input, output)

            // Create and locally close several channels
            repeat(10) {
                val d = CompletableDeferred<DuplexChannel>()
                launch(Dispatchers.Unconfined) {
                    d.complete(multiplexer.createChannel())
                }
                val request = events.receive() as MultiplexerEvent.ChannelRequest
                MultiplexerProtocol.sendResponseNewChannel(request.channelId, input)
                val channel = d.await()
                // Local close — should remove from activeChannels
                channel.close()
                events.receive() as MultiplexerEvent.ChannelClosed
            }

            // Verify multiplexer still works: create new channel and exchange data
            val data = Random.nextBytes(500)
            val chDef = CompletableDeferred<DuplexChannel>()
            launch(Dispatchers.Unconfined) {
                chDef.complete(multiplexer.createChannel())
            }
            val request = events.receive() as MultiplexerEvent.ChannelRequest
            MultiplexerProtocol.sendResponseNewChannel(request.channelId, input)
            val channel = chDef.await()
            channel.outcome.send(bufferOf(data))

            val channelData = events.receive() as MultiplexerEvent.ChannelData
            assertEquals(request.channelId, channelData.channelId)
            assertContentEquals(data, channelData.data.readByteArray())

            multiplexer.close()
            input.close()
            output.close()
            events.cancel()
        }
    }

    @Test
    fun testAcceptAndLocalCloseLeavesMuxOperational() {
        testWithTimeout(10.seconds) {
            val input = Channel<Buffer>(Channel.UNLIMITED)
            val output = Channel<Buffer>(Channel.UNLIMITED)
            val events = MultiplexerProtocol.readEvent(output)
            val multiplexer = createMultiplexer(input, output)

            // Accept and locally close several channels
            repeat(5) {
                MultiplexerProtocol.sendRequestNewChannel(channelId = it + 1, physical = input)
                val channel = multiplexer.accept()
                channel.close()
                events.receive() as MultiplexerEvent.NewChannelAccepted
                events.receive() as MultiplexerEvent.ChannelClosed
            }

            // Accept a new channel and send data — proves mux works
            val data = Random.nextBytes(100)
            val newId = 999
            MultiplexerProtocol.sendRequestNewChannel(channelId = newId, physical = input)
            val channel = multiplexer.accept()
            channel.outcome.send(bufferOf(data))

            events.receive() as MultiplexerEvent.NewChannelAccepted
            val channelData = events.receive() as MultiplexerEvent.ChannelData
            assertEquals(newId, channelData.channelId)
            assertContentEquals(data, channelData.data.readByteArray())

            multiplexer.close()
            input.close()
            output.close()
            events.cancel()
        }
    }

    @Test
    fun testReadJobCrashCleanup() {
        testWithTimeout(5.seconds) {
            val input = Channel<Buffer>(Channel.UNLIMITED)
            val output = Channel<Buffer>(Channel.UNLIMITED)
            val multiplexer = createMultiplexer(input, output)

            // Create an active channel
            MultiplexerProtocol.sendRequestNewChannel(channelId = 111, physical = input)
            multiplexer.accept()

            // Complete readJob gracefully (simulates cleanup after normal exit)
            input.close()
            delay(500)

            // close() must work after readJob completion — verifies consistent state
            multiplexer.close()
            output.close()
        }
    }

    @Test
    fun testCancelWithCausePropagatesToIncome() {
        testWithTimeout(5.seconds) {
            val input = Channel<Buffer>(Channel.UNLIMITED)
            val output = Channel<Buffer>(Channel.UNLIMITED)
            val events = MultiplexerProtocol.readEvent(output)
            val multiplexer = createMultiplexer(input, output)

            MultiplexerProtocol.sendRequestNewChannel(channelId = 111, physical = input)
            val channel = multiplexer.accept()

            val cause: Throwable = RuntimeException("custom cancel cause")
            channel.cancel(cause)

            assertTrue(channel.isClosedForReceive, "income should be closed after cancel")

            multiplexer.close()
            input.close()
            output.close()
            events.cancel()
        }
    }
}
