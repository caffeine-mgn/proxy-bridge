package pw.binom.multiplexer

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.*

class Link {
    val inputA = Channel<Buffer>(Channel.UNLIMITED)
    val outputA = Channel<Buffer>(Channel.UNLIMITED)
    val inputB = Channel<Buffer>(Channel.UNLIMITED)
    val outputB = Channel<Buffer>(Channel.UNLIMITED)

    private val connectJobA = CoroutineScope(Dispatchers.Default).launch {
        for (buffer in outputA) {
            inputB.send(buffer)
        }
    }
    private val connectJobB = CoroutineScope(Dispatchers.Default).launch {
        for (buffer in outputB) {
            inputA.send(buffer)
        }
    }

    fun close() {
        connectJobA.cancel()
        connectJobB.cancel()
        outputA.close()
        outputB.close()
        inputA.close()
        inputB.close()
    }
}

class MultiplexerIntegrationTest {

    private lateinit var link: Link
    private lateinit var muxA: MultiplexerImpl
    private lateinit var muxB: MultiplexerImpl

    @BeforeTest
    fun setup() {
        link = Link()
        muxA = MultiplexerImpl(
            input = link.inputA,
            output = link.outputA,
            idOdd = true,
            ioCoroutineScope = CoroutineScope(Dispatchers.Default),
        )
        muxB = MultiplexerImpl(
            input = link.inputB,
            output = link.outputB,
            idOdd = false,
            ioCoroutineScope = CoroutineScope(Dispatchers.Default),
        )
    }

    @AfterTest
    fun shutdown() {
        muxA.close()
        muxB.close()
        link.close()
    }

    @Test
    fun testCreateAndAcceptBidirectional() {
        runBlocking {
            val dataAtoB = Random.nextBytes(500)
            val dataBtoA = Random.nextBytes(300)

            val chADef = CompletableDeferred<DuplexChannel>()
            val chBDef = CompletableDeferred<DuplexChannel>()

            CoroutineScope(Dispatchers.Unconfined).launch {
                chADef.complete(muxA.createChannel())
            }
            CoroutineScope(Dispatchers.Unconfined).launch {
                chBDef.complete(muxB.accept())
            }

            val channelA = chADef.await()
            val channelB = chBDef.await()

            channelA.outcome.send(bufferOf(dataAtoB))
            assertContentEquals(dataAtoB, channelB.income.receive().readByteArray(), "A→B data mismatch")

            channelB.outcome.send(bufferOf(dataBtoA))
            assertContentEquals(dataBtoA, channelA.income.receive().readByteArray(), "B→A data mismatch")
        }
    }

    @Test
    fun testMultipleChannelsBidirectional() {
        runBlocking {
            val n = 5
            val dataSets = (1..n).map {
                Pair(Random.nextBytes(100), Random.nextBytes(100))
            }

            val chADefs = dataSets.map {
                val d = CompletableDeferred<DuplexChannel>()
                CoroutineScope(Dispatchers.Unconfined).launch {
                    d.complete(muxA.createChannel())
                }
                d
            }
            val chBDefs = dataSets.map {
                val d = CompletableDeferred<DuplexChannel>()
                CoroutineScope(Dispatchers.Unconfined).launch {
                    d.complete(muxB.accept())
                }
                d
            }

            val channelsA = chADefs.awaitAll()
            val channelsB = chBDefs.awaitAll()

            assertEquals(n, channelsA.size)
            assertEquals(n, channelsB.size)

            dataSets.forEachIndexed { index, (dataAtoB, dataBtoA) ->
                val chA = channelsA[index]
                val chB = channelsB[index]

                chA.outcome.send(bufferOf(dataAtoB))
                assertContentEquals(dataAtoB, chB.income.receive().readByteArray(), "A→B on channel $index")

                chB.outcome.send(bufferOf(dataBtoA))
                assertContentEquals(dataBtoA, chA.income.receive().readByteArray(), "B→A on channel $index")
            }

            channelsA.forEach { it.close() }
        }
    }

    @Test
    fun testClosePropagationToRemote() {
        runBlocking {
            val chADef = CompletableDeferred<DuplexChannel>()
            val chBDef = CompletableDeferred<DuplexChannel>()

            CoroutineScope(Dispatchers.Unconfined).launch {
                chADef.complete(muxA.createChannel())
            }
            CoroutineScope(Dispatchers.Unconfined).launch {
                chBDef.complete(muxB.accept())
            }

            val channelA = chADef.await()
            val channelB = chBDef.await()

            channelA.close()

            try {
                channelB.income.receive()
            } catch (_: CancellationException) {
                // Expected
            }

            // Need concurrent accept on muxB for the second createChannel
            CoroutineScope(Dispatchers.Unconfined).launch {
                muxB.accept()
            }
            val testChannel = muxA.createChannel()
            assertNotNull(testChannel)
        }
    }

    @Test
    fun testLargePayloadBidirectional() {
        runBlocking {
            val dataAtoB = Random.nextBytes(131072)
            val dataBtoA = Random.nextBytes(131072)

            val chADef = CompletableDeferred<DuplexChannel>()
            val chBDef = CompletableDeferred<DuplexChannel>()

            CoroutineScope(Dispatchers.Unconfined).launch {
                chADef.complete(muxA.createChannel())
            }
            CoroutineScope(Dispatchers.Unconfined).launch {
                chBDef.complete(muxB.accept())
            }

            val channelA = chADef.await()
            val channelB = chBDef.await()

            channelA.outcome.send(bufferOf(dataAtoB))
            assertContentEquals(dataAtoB, channelB.income.receive().readByteArray(), "Large A→B")

            channelB.outcome.send(bufferOf(dataBtoA))
            assertContentEquals(dataBtoA, channelA.income.receive().readByteArray(), "Large B→A")
        }
    }
}
