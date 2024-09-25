package pw.binom.proxy.channels

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.atomic.AtomicBoolean
import pw.binom.io.AsyncChannel
import pw.binom.io.ByteBuffer
import pw.binom.io.DataTransferSize
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.io.http.websocket.WebSocketInput
import pw.binom.io.useAsync
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.proxy.BridgeJob
import pw.binom.proxy.ChannelId
import pw.binom.proxy.ChannelRole
import pw.binom.proxy.StreamBridge
import pw.binom.proxy.io.copyTo
import kotlin.coroutines.resume

class WSSpitChannel(
    override val id: ChannelId,
    val connection: WebSocketConnection,
    val logger: Logger = Logger.getLogger("WSSpitChannel"),
) : TransportChannel {

    private var currentMessage: WebSocketInput? = null

    private var holder: CancellableContinuation<Unit>? = null
    private val closed = AtomicBoolean(false)
    private var connectCopyJob: Deferred<StreamBridge.ReasonForStopping>? = null

    override fun toString(): String = "WSSpitChannel($connection)"

    override fun breakCurrentRole() {
        connectCopyJob?.cancel()
        connectCopyJob = null
    }

    override suspend fun connectWith(
        other: AsyncChannel,
        bufferSize: Int,
    ) = BridgeJob {
        supervisorScope {
            StreamBridge.copy(
                left = this@WSSpitChannel,
                right = other,
                bufferSize = bufferSize,
                leftProvider = {
                    connectCopyJob = it
                },
                logger = logger,
            ) == StreamBridge.ReasonForStopping.LEFT
        }
    }

    override val available: Int
        get() = currentMessage?.available ?: -1

    suspend fun awaitClose() {
        if (closed.getValue()) {
            return
        }
        suspendCancellableCoroutine {
            holder = it
        }
    }

    override suspend fun asyncClose() {
        if (closed.compareAndSet(false, true)) {
            holder?.resume(Unit)
            connection.asyncCloseAnyway()
        }
    }

    override suspend fun flush() {
        // Do nothing
    }

    override suspend fun read(dest: ByteBuffer): DataTransferSize {
        while (true) {
            val current = currentMessage
            if (current != null) {
                val result = current.read(dest)
                if (result.isNotAvailable) {
                    currentMessage = null
                    current.asyncClose()
                    continue
                }
                if (current.available == 0) {
                    currentMessage = null
                }
                return result
            }
            val msg = try {
                connection.read()
            } catch (e: Throwable) {
                asyncClose()
                return DataTransferSize.CLOSED
            }
            when (msg.type) {
                MessageType.CLOSE -> {
                    msg.asyncCloseAnyway()
                    asyncClose()
                    return DataTransferSize.CLOSED
                }

                MessageType.PING -> {
                    msg.useAsync { msg ->
                        connection.write(MessageType.PONG).useAsync {
                            msg.copyTo(it) {}
                        }
                    }
                    continue
                }

                else -> {
                    currentMessage = msg
                    val result = msg.read(dest)
                    if (result.isNotAvailable) {
                        logger.info("!!!!!")
                        currentMessage = null
                        continue
                    }
                    if (msg.available == 0) {
                        currentMessage = null
                        msg.asyncClose()
                    }
                    return result
                }
            }
        }
    }

    override suspend fun write(data: ByteBuffer): DataTransferSize =
        if (!data.hasRemaining) {
            logger.info("Send ${data.remaining} bytes")
            DataTransferSize.EMPTY
        } else {
            logger.info("Send ${data.remaining} bytes")
//            logger.info("write ${data.toByteArray().toHexString()}")
            connection.write(MessageType.BINARY).useAsync {
                val e = DataTransferSize.ofSize(it.writeFully(data))
                e
            }
        }

}
