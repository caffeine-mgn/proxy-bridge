package pw.binom.gateway

import io.klogging.logger
import io.ktor.network.selector.*
import kotlinx.coroutines.*
import pw.binom.channel.ChannelSelector
import pw.binom.channel.TcpConnectChannel
import pw.binom.multiplexer.DuplexChannel
import pw.binom.multiplexer.Multiplexer
import kotlin.io.println
import kotlin.use

private val logger = logger("Main")
suspend fun main(args: Array<String>) {
    withContext(Dispatchers.IO) {
        SelectorManager(Dispatchers.IO).use { selector ->
            val adapter = pw.binom.bluetooth.BluetoothAdapter.getAdapters().first()
            adapter.listenSPP().use { server ->
                while (coroutineContext.isActive) {
                    logger.info { "Wait a client..." }
                    val newClient = server.accept()
                    logger.info { "Client connected!" }
                    launch {
                        Multiplexer(
                            channel = newClient,
                            idOdd = true,
                            ioCoroutineScope = CoroutineScope(Dispatchers.IO)
                        ).use { multiplexer ->
                            while (isActive) {
                                val newClient = multiplexer.accept()
                                CoroutineScope(Dispatchers.IO).launch {
                                    clientProcessing(newClient, selector)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

suspend fun clientProcessing(connection: DuplexChannel, selector: SelectorManager) {
    ChannelSelector.processing(connection, selector)
}
