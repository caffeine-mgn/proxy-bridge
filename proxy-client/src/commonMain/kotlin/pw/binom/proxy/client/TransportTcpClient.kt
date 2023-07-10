package pw.binom.proxy.client
/*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import pw.binom.io.AsyncChannel
import pw.binom.io.LazyAsyncInput
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.logger.Logger
import pw.binom.logger.debug
import pw.binom.logger.info
import pw.binom.logger.warn
import pw.binom.network.SocketClosedException
import pw.binom.proxy.io.copyTo
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

class TransportTcpClient private constructor(
    private val socket: AsyncChannel,
    private val connection: AsyncChannel,
    private val onClose: (TransportTcpClient) -> Unit,
    private val logger: Logger,
    private val bufferSize: Int,
) : TransportClient {
    private var wsToTcp: Job? = null
    private var tcpToWs: Job? = null

    private suspend fun start() {
        wsToTcp = GlobalScope.launch(coroutineContext) {
            try {
                logger.info("Start coping.... ${connection.hashCode()}->${socket.hashCode()}")
                connection.copyTo(socket, bufferSize = bufferSize) {
                    logger.debug("client<-remote $it")
                }
            } catch (e: SocketClosedException) {
                // Do nothing
            } catch (e: CancellationException) {
                // Do nothing
            } catch (e: Throwable) {
                logger.warn("Error on client<-remote", exception = e)
            } finally {
                logger.info("Transport client finished!")
                connection.asyncCloseAnyway()
                socket.asyncCloseAnyway()
                onClose(this@TransportTcpClient)
            }
        }
        tcpToWs = GlobalScope.launch(coroutineContext) {
            try {
                logger.info("Start coping.... ${socket.hashCode()}->${connection.hashCode()}")
                socket.copyTo(connection, bufferSize = bufferSize) {
                    logger.debug("client->remote $it")
                }
            } catch (e: SocketClosedException) {
                // Do nothing
            } catch (e: Throwable) {
                logger.warn("Error on client->remote", exception = e)
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
            bufferSize: Int,
            onClose: (TransportTcpClient) -> Unit,
        ): TransportTcpClient {
            val client = TransportTcpClient(
                socket = socket,
                connection = transportConnection,
                onClose = onClose,
                logger = logger,
                bufferSize = bufferSize,
            )
            client.start()
            return client
        }

        suspend fun start(
            socket: AsyncChannel,
            transportConnection: WebSocketConnection,
            logger: Logger,
            bufferSize: Int,
            onClose: (TransportTcpClient) -> Unit,
        ): TransportClient {
            val input = LazyAsyncInput {
                logger.info("Wating single message...")
                val msg = transportConnection.read()
                logger.info("Single message started!")
                msg
            }
            val output = transportConnection.write(MessageType.BINARY)

            val serverChannel = AsyncChannel.create(
                input = input,
                output = output,
            ) {
                input.asyncCloseAnyway()
                output.asyncCloseAnyway()
                transportConnection.asyncCloseAnyway()
            }

            return start(
                socket = socket,
                transportConnection = serverChannel,
                logger = logger,
                bufferSize = bufferSize,
                onClose = onClose,
            )
        }

    }
}
*/
