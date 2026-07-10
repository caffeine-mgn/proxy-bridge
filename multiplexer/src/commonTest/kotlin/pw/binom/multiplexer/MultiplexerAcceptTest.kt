package pw.binom.multiplexer

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class MultiplexerAcceptTest {

    @Test
    fun testDataBeforeAccept() {
        val d = newSingleThreadContext("test")
        try {
            runBlocking(d) {
                val input = Channel<Buffer>(Channel.UNLIMITED)
                val output = Channel<Buffer>(Channel.UNLIMITED)

                val multiplexer = MultiplexerImpl(
                    input = input,
                    output = output,
                    idOdd = true,
                    ioCoroutineScope = CoroutineScope(Dispatchers.Default),
                )

withTimeout(5.seconds) {
                    val data = Random.nextBytes(500)
                    // DATA ДО accept() — раньше терялось из-за гонки
                    MultiplexerProtocol.sendRequestNewChannel(channelId = 111, physical = input)
                    input.send(MultiplexerProtocol.wrapLogicalToPhysical(channelId = 111, data = bufferOf(data)))
                    val channel = multiplexer.accept()
                    val received = channel.income.receive().readByteArray()
                    assertContentEquals(data, received)
                }

                multiplexer.close()
                input.close()
                output.close()
            }
        } finally {
            d.close()
        }
    }

    @Test
    fun testLargePayloadBeforeAccept() {
        val d = newSingleThreadContext("test")
        try {
            runBlocking(d) {
                val input = Channel<Buffer>(Channel.UNLIMITED)
                val output = Channel<Buffer>(Channel.UNLIMITED)

                val multiplexer = MultiplexerImpl(
                    input = input,
                    output = output,
                    idOdd = true,
                    ioCoroutineScope = CoroutineScope(Dispatchers.Default),
                )

withTimeout(10.seconds) {
                    val data = Random.nextBytes(65536)
                    MultiplexerProtocol.sendRequestNewChannel(channelId = 111, physical = input)
                    input.send(MultiplexerProtocol.wrapLogicalToPhysical(channelId = 111, data = bufferOf(data)))
                    val channel = multiplexer.accept()
                    val received = channel.income.receive().readByteArray()
                    assertContentEquals(data, received)
                }

                multiplexer.close()
                input.close()
                output.close()
            }
        } finally {
            d.close()
        }
    }

    @Test
    fun testMultipleDataBeforeAccept() {
        val d = newSingleThreadContext("test")
        try {
            runBlocking(d) {
                val input = Channel<Buffer>(Channel.UNLIMITED)
                val output = Channel<Buffer>(Channel.UNLIMITED)

                val multiplexer = MultiplexerImpl(
                    input = input,
                    output = output,
                    idOdd = true,
                    ioCoroutineScope = CoroutineScope(Dispatchers.Default),
                )

withTimeout(5.seconds) {
                    val data1 = Random.nextBytes(100)
                    val data2 = Random.nextBytes(200)
                    // Два DATA-пакета до accept()
                    MultiplexerProtocol.sendRequestNewChannel(channelId = 111, physical = input)
                    input.send(MultiplexerProtocol.wrapLogicalToPhysical(channelId = 111, data = bufferOf(data1)))
                    input.send(MultiplexerProtocol.wrapLogicalToPhysical(channelId = 111, data = bufferOf(data2)))
                    val channel = multiplexer.accept()
                    assertContentEquals(data1, channel.income.receive().readByteArray())
                    assertContentEquals(data2, channel.income.receive().readByteArray())
                }

                multiplexer.close()
                input.close()
                output.close()
            }
        } finally {
            d.close()
        }
    }
}
