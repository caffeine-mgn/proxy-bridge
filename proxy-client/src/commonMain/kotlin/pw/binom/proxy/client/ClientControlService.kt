package pw.binom.proxy.client

import kotlinx.coroutines.*
import pw.binom.*
import pw.binom.io.ByteBuffer
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketClosedException
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.connectWebSocket
import pw.binom.io.socket.NetworkAddress
import pw.binom.io.socket.UnknownHostException
import pw.binom.io.use
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.severe
import pw.binom.logger.warn
import pw.binom.network.NetworkManager
import pw.binom.network.SocketConnectException
import pw.binom.proxy.Codes
import pw.binom.proxy.ControlClient
import pw.binom.proxy.ControlResponseCodes
import pw.binom.proxy.Urls
import pw.binom.strong.Strong
import pw.binom.strong.inject
import pw.binom.url.toURL

class ClientControlService : Strong.LinkingBean, Strong.DestroyableBean {
    val httpClient by inject<HttpClient>()
    val transportService by inject<TransportService>()
    val runtimeProperties by inject<RuntimeProperties>()
    val selfReplaceService by inject<FileService>()
    val networkManager by inject<NetworkManager>()
    val logger by Logger.ofThisOrGlobal

    private suspend fun connectProcessing(
        id: Int,
        buffer: ByteBuffer,
        connection: WebSocketConnection,
        host: String,
        port: Int,
        channelId: Int,
    ) {
        val connectResult =
            runCatching {
                transportService.connect(
                    id = channelId,
                    address =
                        NetworkAddress.create(
                            host = host,
                            port = port
                        )
                )
            }
        when {
            connectResult.isSuccess -> {
                connection.write(MessageType.BINARY).use { msg ->
                    msg.writeInt(id, buffer = buffer)
                    msg.writeByte(ControlResponseCodes.OK.code, buffer = buffer)
                }
            }

            connectResult.isFailure -> {
                when (val e = connectResult.exceptionOrNull()) {
                    is UnknownHostException -> {
                        connection.write(MessageType.BINARY).use { msg ->
                            msg.writeInt(id, buffer = buffer)
                            msg.writeByte(ControlResponseCodes.UNKNOWN_HOST.code, buffer = buffer)
                        }
                        logger.info(text = "Unknown host $host:$port")
                    }

                    else -> {
                        connection.write(MessageType.BINARY).use { msg ->
                            msg.writeInt(id, buffer = buffer)
                            msg.writeByte(ControlResponseCodes.UNKNOWN_ERROR.code, buffer = buffer)
                        }
                        logger.info(text = "Can't connect to $host:$port", exception = e)
                    }
                }
            }
        }
    }

    private var clientProcess: Job? = null

    private suspend fun createConnection() {
        val url = "${runtimeProperties.url}${Urls.CONTROL}".toURL()
        logger.info("Connection to $url...")
        val connection =
            httpClient.connectWebSocket(
                uri = url
            ).start(bufferSize = runtimeProperties.bufferSize)
        logger.info("Connected!")
        ControlClient(
            connection = connection,
            networkManager = networkManager,
            pingInterval = runtimeProperties.pingInterval,
            logger = Logger.getLogger("Client")
        ).use { client ->
            try {
                client.runClient(
                    handler =
                        ControlClient.BaseHandler.composite(connect = { host, port, channelId ->
                            transportService.connect(
                                id = channelId,
                                address =
                                    NetworkAddress.create(
                                        host = host,
                                        port = port
                                    )
                            )
                        })
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.severe(text = "Error on package reading", exception = e)
            } finally {
                withContext(NonCancellable) {
                    logger.warn("Control finished!")
                    connection.asyncCloseAnyway()
                }
            }
        }
        return

        logger.info("Connected to $url")
        ByteBuffer(1024 * 1024).use { buffer ->
            try {
                while (true) {
                    try {
                        connection.read().use { msg ->
                            try {
                                logger.info("Income message!")
                                when (val byte = msg.readByte(buffer)) {
                                    Codes.CONNECT -> {
                                        val id = msg.readInt(buffer)
                                        val host = msg.readUTF8String(buffer)
                                        val port = msg.readShort(buffer).toInt()
                                        val channelId = msg.readInt(buffer)
                                        connectProcessing(
                                            id = id,
                                            buffer = buffer,
                                            connection = connection,
                                            host = host,
                                            port = port,
                                            channelId = channelId
                                        )
                                    }

                                    Codes.PUT_FILE -> {
                                        val id = msg.readInt(buffer)
                                        val fileStr = msg.readUTF8String(buffer)
                                        try {
                                            selfReplaceService.putFile(fileDest = fileStr, input = msg, buffer = buffer)
                                            connection.write(MessageType.BINARY).use { msg ->
                                                msg.writeInt(id, buffer = buffer)
                                                msg.writeByte(1, buffer = buffer)
                                            }
                                        } catch (e: Throwable) {
                                            logger.info("ERROR: ", e)
                                            connection.write(MessageType.BINARY).use { msg ->
                                                msg.writeInt(id, buffer = buffer)
                                                msg.writeByte(0, buffer = buffer)
                                            }
                                        }
                                    }

                                    else -> TODO("Unknown cmd $byte")
                                }
                            } catch (e: Throwable) {
                                logger.info(text = "Error during message reading...", exception = e)
                                throw e
                            } finally {
                                logger.info("Message processed!")
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        logger.info(text = "Message processed! Wait new message...", exception = e)
                        throw e
                    } finally {
                        logger.info("Message processed! Wait new message...")
                    }
                }
            } catch (e: WebSocketClosedException) {
                logger.severe(text = "Ws Connection closed. isControlWs=${e.connection === connection}", exception = e)
            } catch (e: DestroyingException) {
                // Do nothing
            } catch (e: TimeoutCancellationException) {
                logger.warn("Message reading timeout!!! Closing!!!!")
                connection.asyncCloseAnyway()
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
        clientProcess =
            GlobalScope.launch(networkManager + CoroutineName(logger.pkg)) {
                while (isActive) {
                    logger.info("Try make connect")
                    try {
                        createConnection()
                        delay(runtimeProperties.reconnectTimeout)
                    } catch (e: SocketConnectException) {
                        e.printStackTrace()
                        logger.warn(text = "Can't connect to server: ${e.message}")
                        delay(runtimeProperties.reconnectTimeout)
                    } catch (e: WebSocketClosedException) {
                        e.printStackTrace()
                        logger.warn("Connection lost")
                        delay(runtimeProperties.reconnectTimeout)
                    } catch (e: CancellationException) {
                        break
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        logger.warn("Error connection", exception = e)
                        delay(runtimeProperties.reconnectTimeout)
                    } finally {
                        logger.info("Connection finished!")
                    }
                }
            }
    }

    override suspend fun destroy(strong: Strong) {
        println("ClientControlService::destroy Closing ClientConnection clientProcess=$clientProcess")
        clientProcess?.cancel(DestroyingException())
        clientProcess?.join()
        println("ClientControlService::destroy clientProcess=$clientProcess")
        clientProcess = null
    }

    class DestroyingException : CancellationException(null as String?)
}
