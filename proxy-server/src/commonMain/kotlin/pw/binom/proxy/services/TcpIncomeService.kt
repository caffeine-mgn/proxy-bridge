package pw.binom.proxy.services

import pw.binom.io.ioDispatcher
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pw.binom.ByteChannelDuplexChannel
import pw.binom.multiplexer.MultiplexerImpl
import pw.binom.properties.CurrentMultiplexer
import kotlin.use

class TcpIncomeService(
    private val port: Int,
    private val host: String,
    private val selectorManager: SelectorManager,
    private val channelSelectorService: ChannelSelectorService,
) : IncomeService {
    override fun close() {
        job.cancel()
    }

    private val job = CoroutineScope(ioDispatcher).launch {
        aSocket(selectorManager).tcp().bind(hostname = host, port = port) { reuseAddress = true }.use { server ->
            val newClient = server.accept()
            newClient.use { newClient ->
                ByteChannelDuplexChannel.create(
                    rr = newClient.openReadChannel(),
                    ww = newClient.openWriteChannel()
                ).use { rawChannel ->
                    MultiplexerImpl(
                        channel = rawChannel,
                        idOdd = true,
                        ioCoroutineScope = CoroutineScope(ioDispatcher)
                    ).use { multiplexer ->
                        val con = multiplexer.accept()
                        CoroutineScope(ioDispatcher).launch {
                            while (isActive) {
                                CoroutineScope(ioDispatcher).launch(CurrentMultiplexer(multiplexer)) {
                                    con.use { channel ->
                                        channelSelectorService.processing(
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
