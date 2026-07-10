package pw.binom.proxy.services

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pw.binom.ByteChannelDuplexChannel
import pw.binom.channel.ChannelSelector
import pw.binom.multiplexer.MultiplexerImpl
import pw.binom.properties.CurrentMultiplexer
import kotlin.use

class TcpIncomeService(
    private val port: Int,
    private val host: String,
    private val selectorManager: SelectorManager,
    private val channelSelector: ChannelSelector,
) : IncomeService {
    override fun close() {
        job.cancel()
    }

    private val job = CoroutineScope(Dispatchers.IO).launch {
        aSocket(selectorManager).tcp().bind(hostname = host, port = port).use { server ->
            val newClient = server.accept()
            newClient.use { newClient ->
                ByteChannelDuplexChannel.create(
                    rr = newClient.openReadChannel(),
                    ww = newClient.openWriteChannel()
                ).use { rawChannel ->
                    MultiplexerImpl(
                        channel = rawChannel,
                        idOdd = true,
                        ioCoroutineScope = CoroutineScope(Dispatchers.IO)
                    ).use { multiplexer ->
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
                    }
                }
            }
        }
    }
}
