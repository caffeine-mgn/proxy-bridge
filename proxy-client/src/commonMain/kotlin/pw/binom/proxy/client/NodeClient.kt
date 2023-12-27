package pw.binom.proxy.client

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import pw.binom.*
import pw.binom.io.AsyncCloseable
import pw.binom.io.ByteBuffer
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.io.use
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.warn
import pw.binom.network.NetworkManager
import pw.binom.proxy.Codes
import pw.binom.proxy.ControlResponseCodes
import kotlin.coroutines.cancellation.CancellationException

class NodeClient private constructor(
    val connection: WebSocketConnection,
    networkManager: NetworkManager,
    val handler: Handler,
) : AsyncCloseable {
    interface Handler {
        suspend fun connect(channelId: Int, host: String, port: Int): Boolean
        suspend fun pong(id: Long)
//        suspend fun closed()
    }

    companion object {
        private val logger = Logger.getLogger("NodeClient")

        private suspend fun sendResponseCode(id: Int, code: Byte, buffer: ByteBuffer, connection: WebSocketConnection) {
            connection.write(MessageType.BINARY).use { output ->
                output.writeInt(id, buffer)
                output.writeByte(code, buffer)
            }
        }

        suspend fun runClient(connection: WebSocketConnection, handler: Handler) {
            ByteBuffer(1024).use { buffer ->
                while (true) {
                    try {
                        connection.read().use MSG@{ msg ->
                            if (msg.type == MessageType.CLOSE) {
                                logger.info("Received close message")
                                return
                            }
                            if (msg.type == MessageType.PING) {
                                buffer.clear()
                                buffer.reset(position = 0, length = Long.SIZE_BYTES)
                                msg.readFully(buffer)
                                buffer.flip()
                                connection.write(MessageType.PONG).use { out ->
                                    out.writeFully(buffer)
                                }
                                return@MSG
                            }
                            if (msg.type == MessageType.PONG) {
                                buffer.clear()
                                buffer.reset(position = 0, length = Long.SIZE_BYTES)
                                msg.readFully(buffer)
                                buffer.flip()
                                val id = buffer.readLong()
                                handler.pong(id)
                                return@MSG
                            }
                            try {
                                logger.info("Income message! ${msg.type}, available=${msg.available}")
                                when (val byte = msg.readByte(buffer)) {
                                    Codes.CONNECT -> {
                                        val id = msg.readInt(buffer)
                                        val host = msg.readUTF8String(buffer)
                                        val port = msg.readShort(buffer).toInt()
                                        val channelId = msg.readInt(buffer)
                                        try {
                                            val ok = handler.connect(
                                                channelId = channelId,
                                                host = host,
                                                port = port,
                                            )
                                            if (ok) {
                                                sendResponseCode(
                                                    id = id,
                                                    code = ControlResponseCodes.OK.code,
                                                    buffer = buffer,
                                                    connection = connection,
                                                )
                                            } else {
                                                sendResponseCode(
                                                    id = id,
                                                    code = ControlResponseCodes.UNKNOWN_HOST.code,
                                                    buffer = buffer,
                                                    connection = connection,
                                                )
                                            }
                                        } catch (e: Throwable) {
                                            logger.warn(text = "Can't connect to $host:$port", exception = e)
                                            sendResponseCode(
                                                id = id,
                                                code = ControlResponseCodes.UNKNOWN_ERROR.code,
                                                buffer = buffer,
                                                connection = connection,
                                            )
                                        }
                                        logger.info("READ FINISHED available=${msg.available}")
                                    }

                                    else -> TODO("Unknown cmd $byte and ${msg.readByte(buffer).toUByte()}")
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
            }
        }
    }

    private val job = GlobalScope.launch(networkManager) {
        runClient(
            connection = connection,
            handler = handler,
        )
    }

    override suspend fun asyncClose() {
        if (job.isActive && !job.isCompleted && !job.isCancelled) {
            job.cancel()
        }
    }
}
