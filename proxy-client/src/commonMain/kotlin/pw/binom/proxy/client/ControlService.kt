package pw.binom.proxy.client

import kotlinx.coroutines.*
import pw.binom.*
import pw.binom.io.ByteBuffer
import pw.binom.io.http.websocket.Message
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketClosedException
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.connectWebSocket
import pw.binom.io.socket.NetworkAddress
import pw.binom.io.use
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.severe
import pw.binom.logger.warn
import pw.binom.network.SocketConnectException
import pw.binom.proxy.Codes
import pw.binom.proxy.Urls
import pw.binom.strong.Strong
import pw.binom.strong.inject
import pw.binom.url.toURL
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

class ControlService : Strong.LinkingBean, Strong.DestroyableBean {
    val httpClient by inject<HttpClient>()
    val transportService by inject<TransportService>()
    val runtimeProperties by inject<RuntimeProperties>()
    val selfReplaceService by inject<FileService>()
    val logger by Logger.ofThisOrGlobal

    private suspend fun connectProcessing(id: Int, buffer: ByteBuffer, msg: Message, connection: WebSocketConnection) {
        val host = msg.readUTF8String(buffer)
        val port = msg.readShort(buffer).toInt()
        val channelId = msg.readInt(buffer)
        try {
            transportService.connect(
                id = channelId,
                transportType = runtimeProperties.transportType,
                address = NetworkAddress.create(
                    host = host,
                    port = port,
                ),
            )
            connection.write(MessageType.BINARY).use { msg ->
                msg.writeInt(id, buffer = buffer)
                msg.writeByte(1, buffer = buffer)
            }
            logger.info("Sent success response")
        } catch (e: Throwable) {
            connection.write(MessageType.BINARY).use { msg ->
                msg.writeInt(id, buffer = buffer)
                msg.writeByte(0, buffer = buffer)
            }
            logger.info(text = "Can't connect to $host:$port", exception = e)
            throw e
        }
    }

    private var clientProcess: Job? = null

    private suspend fun createConnection() {
        val url = "${runtimeProperties.url}${Urls.CONTROL}".toURL()
        logger.info("Connection to $url")
        val connection = httpClient.connectWebSocket(
            uri = url,
        ).start()
        logger.info("Connected to $url")
        ByteBuffer(1024 * 1024).use { buffer ->
            try {
                while (true) {
                    connection.read().use { msg ->
                        logger.info("Income message!")
                        val id = msg.readInt(buffer)
                        when (msg.readByte(buffer)) {
                            Codes.CONNECT -> connectProcessing(
                                id = id,
                                buffer = buffer,
                                msg = msg,
                                connection = connection,
                            )

                            Codes.PUT_FILE -> {
                                val fileStr = msg.readUTF8String(buffer)
                                try {
                                    selfReplaceService.putFile(fileDest = fileStr, input = msg, buffer = buffer)
                                    connection.write(MessageType.BINARY).use { msg ->
                                        msg.writeInt(id, buffer = buffer)
                                        msg.writeByte(1, buffer = buffer)
                                    }
                                } catch (e: Throwable) {
                                    connection.write(MessageType.BINARY).use { msg ->
                                        msg.writeInt(id, buffer = buffer)
                                        msg.writeByte(0, buffer = buffer)
                                    }
                                }
                            }

                            else -> TODO()
                        }
                    }
                    logger.info("Message processed! Wait new message...")
                }
            } catch (e: WebSocketClosedException) {
                logger.severe(text = "Ws Connection closed. isControlWs=${e.connection === connection}", exception = e)
            } catch (e: DestroyingException) {
                // Do nothing
            } catch (e: CancellationException) {
                logger.severe(text = "Control cancelled", exception = e)
                // Do nothing
            } catch (e: Throwable) {
                logger.severe(text = "Error on package reading", exception = e)
            } finally {
                logger.warn("Control finished!")
                connection.asyncCloseAnyway()
            }
        }
    }

    override suspend fun link(strong: Strong) {
        clientProcess = GlobalScope.launch(coroutineContext) {
            while (isActive) {
                try {
                    createConnection()
                } catch (e: DestroyingException) {
                    break
                } catch (e: SocketConnectException) {
                    logger.warn("Can't connect to server")
                    delay(10.seconds)
                } catch (e: WebSocketClosedException) {
                    logger.warn("Connection lost")
                    delay(5.seconds)
                } catch (e: CancellationException) {
                    break
                } catch (e: Throwable) {
                    logger.warn("Error connection", exception = e)
                    delay(10.seconds)
                }
            }
        }
    }

    override suspend fun destroy(strong: Strong) {
        clientProcess?.cancel(DestroyingException())
        clientProcess = null
    }

    class DestroyingException : CancellationException(null as String?)
}