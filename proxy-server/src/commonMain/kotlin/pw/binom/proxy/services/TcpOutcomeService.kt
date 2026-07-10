package pw.binom.proxy.services

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pw.binom.ByteChannelDuplexChannel
import pw.binom.channel.ChannelSelector
import pw.binom.multiplexer.DuplexChannel
import pw.binom.multiplexer.Multiplexer
import pw.binom.multiplexer.MultiplexerImpl
import pw.binom.properties.CurrentMultiplexer
import pw.binom.properties.OutcomeService
import java.lang.AutoCloseable
import kotlin.time.Duration.Companion.seconds
import kotlin.use

class TcpOutcomeService(
    private val port: Int,
    private val host: String,
    private val selectorManager: SelectorManager,
    private val channelSelector: ChannelSelector,
    override val name: String,
) : OutcomeService, AutoCloseable {
    private val logger = KotlinLogging.logger {}
    private var currentMultiplexer: Multiplexer? = null
    private val job = CoroutineScope(Dispatchers.IO).launch {
        while (isActive) {
            val socket = try {
                aSocket(selectorManager).tcp().connect(host, port)
            } catch (e: Throwable) {
                logger.warn(e) { "Can't connect to $host:$port" }
                delay(5.seconds)
                continue
            }

            socket.use { socket ->
                ByteChannelDuplexChannel.create(
                    rr = socket.openReadChannel(),
                    ww = socket.openWriteChannel()
                ).use { rawChannel ->

                    MultiplexerImpl(
                        channel = rawChannel,
                        idOdd = false,
                        ioCoroutineScope = CoroutineScope(Dispatchers.IO)
                    ).use { multiplexer ->
                        try {
                            currentMultiplexer = multiplexer
                            val con = multiplexer.accept()
                            CoroutineScope(Dispatchers.IO).launch {
                                while (isActive) {
                                    CoroutineScope(Dispatchers.IO).launch(CurrentMultiplexer(multiplexer)) {
                                        con.use { channel ->
                                            channelSelector.processing(
                                                connection = channel,
                                            )
                                        }
                                    }
                                }
                            }
                        } finally {
                            currentMultiplexer = null
                        }
                    }

                }
            }
        }
    }

    override suspend fun createChannel(): DuplexChannel =
        currentMultiplexer?.createChannel() ?: throw IllegalStateException("Multiplexer not ready")

    override fun close() {
        job.cancel()
    }
}
