package pw.binom.proxy

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
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
import pw.binom.strong.Strong
import pw.binom.strong.inject
import pw.binom.url.toURL
import kotlin.coroutines.coroutineContext

class ControlService : Strong.LinkingBean {
    val httpClient by inject<HttpClient>()
    val transportService by inject<TransportService>()
    val runtimeProperties by inject<RuntimeProperties>()
    val logger by Logger.ofThisOrGlobal

    private suspend fun connectProcessing(id: Int, buffer: ByteBuffer, msg: Message, connection: WebSocketConnection) {
        val host = msg.readUTF8String(buffer)
        val port = msg.readShort(buffer).toInt()
        val channelId = msg.readInt(buffer)
        try {
            transportService.connectTcp(
                    id = channelId,
                    address = NetworkAddress.create(
                            host = host,
                            port = port,
                    )
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

    override suspend fun link(strong: Strong) {
        clientProcess = GlobalScope.launch(coroutineContext) {
            try {
                val url = "${runtimeProperties.url}${Urls.CONTROL}".toURL()
                logger.info("Connection to $url")
                val connection = httpClient.connectWebSocket(
                        uri = url,
                ).start()
                logger.info("Connected to $url")
                ByteBuffer(100).use { buffer ->
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

                                    else -> TODO()
                                }
                            }
                            logger.info("Message processed! Wait new message...")
                        }
                    } catch (e: WebSocketClosedException) {
                        logger.severe(text = "Ws Connection closed. isControlWs=${e.connection === connection}", exception = e)
                    } catch (e: Throwable) {
                        logger.severe(text = "Error on package reading", exception = e)
                    } finally {
                        logger.warn("Control finished!")
                        connection.asyncCloseAnyway()
                    }
                }
            } catch (e: Throwable) {
                logger.severe(text = "Error on ControlService", exception = e)
            }
        }
    }
}