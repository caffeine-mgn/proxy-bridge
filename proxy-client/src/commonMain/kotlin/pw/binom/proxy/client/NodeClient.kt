package pw.binom.proxy.client

import kotlinx.coroutines.*
import pw.binom.*
import pw.binom.atomic.AtomicLong
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.AsyncCloseable
import pw.binom.io.ByteBuffer
import pw.binom.io.ClosedException
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
import kotlin.coroutines.resume
import kotlin.time.Duration

class NodeClient constructor(
    val connection: WebSocketConnection,
    networkManager: NetworkManager,
    pingInterval: Duration,
) : AsyncCloseable {

    private val pingWaitersLock = SpinLock()
    private val pingWaiters = HashMap<Long, CancellableContinuation<Unit>>()
    private var pingCounter = AtomicLong(0)

    suspend fun ping() {
        val pingId = pingCounter.addAndGet(1)
        connection.write(MessageType.PING).use { out ->
            ByteBuffer(MAX_PING_BYTES).use { buffer ->
                buffer.writeLong(pingId)
                buffer.flip()
                out.writeFully(buffer)
            }
        }
        suspendCancellableCoroutine { con ->
            con.invokeOnCancellation {
                pingWaitersLock.synchronize {
                    pingWaiters.remove(pingId)
                }
            }
            pingWaitersLock.synchronize {
                pingWaiters[pingId] = con
            }
        }
    }

    interface Handler {
        suspend fun connect(channelId: Int, host: String, port: Int): Boolean
    }

    companion object {
        private val logger = Logger.getLogger("NodeClient")
        private const val MAX_PING_BYTES = Long.SIZE_BYTES

        private suspend fun sendResponseCode(id: Int, code: Byte, buffer: ByteBuffer, connection: WebSocketConnection) {
            connection.write(MessageType.BINARY).use { output ->
                output.writeInt(id, buffer)
                output.writeByte(code, buffer)
            }
        }

    }

    suspend fun runClient(handler: Handler) {
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
                            buffer.reset(position = 0, length = Long.SIZE_BYTES)
                            msg.readFully(buffer)
                            buffer.flip()
                            if (buffer.remaining != MAX_PING_BYTES) {
                                val pingId = buffer.readLong()
                                pingWaitersLock.synchronize {
                                    pingWaiters.remove(pingId)?.resume(Unit)
                                }
                            }
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
                    asyncCloseAnyway()
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

    private val pingJob = if (pingInterval > Duration.ZERO) GlobalScope.launch(networkManager) {
        while (isActive) {
            try {
                delay(pingInterval)
                ping()
            } catch (e: ClosedException) {
                break
            } catch (e: CancellationException) {
                break
            }
        }
    } else null

    override suspend fun asyncClose() {
        val pingJob = pingJob
        if (pingJob != null) {
            if (pingJob.isActive && !pingJob.isCompleted && !pingJob.isCancelled) {
                pingJob.cancelAndJoin()
            }
        }
    }
}
