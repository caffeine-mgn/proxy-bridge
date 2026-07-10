package pw.binom.proxy.services

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import pw.binom.TcpConnectProvider
import pw.binom.utils.connect

class PortForwardingService(
    private val bindHost: String,
    private val bindPort: Int,
    private val remoteHost: String,
    private val remotePort: Int,
    private val tcpConnectProvider: TcpConnectProvider,
    private val selectorManager: SelectorManager,
) : KoinComponent, AutoCloseable {
    private val logger = KotlinLogging.logger { }
    private val job = CoroutineScope(Dispatchers.IO).launch {
        val serverSocket = try {
            aSocket(selectorManager).tcp().bind(bindHost, bindPort)
        } catch (e: Throwable) {
            logger.error(e) { "Can't bind to $bindHost:$bindPort" }
            return@launch
        }
        logger.info { "Port forwarding started on $bindHost:$bindPort -> $remoteHost:$remotePort" }
        serverSocket.use { server ->
            while (isActive) {
                val newClient = server.accept()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val r = tcpConnectProvider.connect(remoteHost, remotePort)
                        if (r !is TcpConnectProvider.ConnectResult.Success) {
                            return@launch
                        }
                        connect(
                            outcome = r.writeChannel,
                            income = r.readChannel,
                            a = newClient.openWriteChannel(),
                            b = newClient.openReadChannel(),
                        )
                    } catch (e: Throwable) {
                        logger.info(e) { "Connection closed" }
                    }
                }
            }
        }
    }

    override fun close() {
        job.cancel()
    }
}
