package pw.binom.proxy

import kotlinx.coroutines.*
import pw.binom.copyTo
import pw.binom.io.AsyncChannel
import pw.binom.io.AsyncCloseable
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.io.use
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.warn
import pw.binom.network.SocketClosedException
import pw.binom.network.TcpConnection
import pw.binom.proxy.io.*
import kotlin.coroutines.coroutineContext

class TransportTcpClient private constructor(
        val socket: AsyncChannel,
        val connection: AsyncChannel,
        val onClose: (TransportTcpClient) -> Unit,
        val logger: Logger
) : AsyncCloseable {
    private var wsToTcp: Job? = null
    private var tcpToWs: Job? = null

    private suspend fun start() {
        wsToTcp = GlobalScope.launch(coroutineContext) {
            try {
                connection.copyTo(socket) {
                    logger.info("ws->tcp $it")
                }
            } catch (e: SocketClosedException) {
                // Do nothing
            } catch (e: Throwable) {
                logger.warn("Error on ws->tcp", exception = e)
            } finally {
                logger.info("Transport client finished!")
                connection.asyncCloseAnyway()
                socket.asyncCloseAnyway()
                onClose(this@TransportTcpClient)
            }
        }
        tcpToWs = GlobalScope.launch(coroutineContext) {
            try {
                socket.copyTo(connection) {
                    logger.info("tcp->ws $it")
                }
            } catch (e: SocketClosedException) {
                // Do nothing
            } catch (e: Throwable) {
                logger.warn("Error on tcp->ws", exception = e)
            }
        }
    }

    override suspend fun asyncClose() {
        logger.info("Closing connection")
        socket.asyncCloseAnyway()
        connection.asyncCloseAnyway()
    }

    companion object {
        suspend fun start(
                socket: AsyncChannel,
                transportConnection: AsyncChannel,
                logger: Logger,
                onClose: (TransportTcpClient) -> Unit
        ): TransportTcpClient {

            val client = TransportTcpClient(
                    socket = socket,
                    connection = transportConnection,
                    onClose = onClose,
                    logger = logger,
            )
            client.start()
            return client
        }
    }
}