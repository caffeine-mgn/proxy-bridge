package pw.binom.proxy.channels

import kotlinx.coroutines.*
import pw.binom.atomic.AtomicBoolean
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.io.http.websocket.WebSocketInput
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.proxy.TransportChannelId
import pw.binom.StreamBridge
import pw.binom.atomic.AtomicLong
import pw.binom.io.*
import pw.binom.io.http.websocket.WebSocketClosedException
import pw.binom.proxy.io.copyTo
import kotlin.coroutines.resume

class WSSpitChannel(
    override val id: TransportChannelId,
    val connection: WebSocketConnection,
    val logger: Logger = Logger.getLogger("WSSpitChannel"),
) : TransportChannel {

    override var description: String? = null
    override val isClosed: Boolean
        get() = closed.getValue()

    private val internalInput = AtomicLong(0)
    private val internalOutput = AtomicLong(0)

    override val input: Long
        get() = internalInput.getValue()
    override val output: Long
        get() = internalOutput.getValue()

    private var currentMessage: WebSocketInput? = null

    private var holder: CancellableContinuation<Unit>? = null
    private val closed = AtomicBoolean(false)
    private var connectCopyJob: Deferred<StreamBridge.ReasonForStopping>? = null
    private var connectCopyJob1: Deferred<StreamBridge.ReasonForStopping>? = null
    private var remoteConnection: AsyncChannel? = null

    override fun toString(): String = "WSSpitChannel($connection)"
    /*
        override suspend fun breakCurrentRole() {
            val connectCopyJob0 = connectCopyJob
            val connectCopyJob1 = connectCopyJob1
            val description = description
            val remoteConnection = remoteConnection
            logger.infoSync("WSSpitChannel: stop connectCopyJob0=${connectCopyJob0?.hashCode()} $id $description")
            logger.infoSync("WSSpitChannel: stop connectCopyJob1=${connectCopyJob1?.hashCode()} $id $description")
            connectCopyJob0?.cancel()
            connectCopyJob1?.cancel()
            if (remoteConnection != null) {
                logger.info("WSSpitChannel: Closing $remoteConnection")
                remoteConnection.asyncCloseAnyway()
            } else {
                logger.info("WSSpitChannel: Can't close remote connection: no remote connection")
            }

            this.description = null
            this.connectCopyJob = null
            this.connectCopyJob1 = null
            this.remoteConnection = null

        }

        override suspend fun connectWith(
            other: AsyncChannel,
            bufferSize: Int,
        ) = BridgeJob {
            remoteConnection = other
            supervisorScope {
                StreamBridge.sync(
                    left = this@WSSpitChannel,
                    right = other,
                    bufferSize = bufferSize,
                    leftProvider = {
                        logger.infoSync("WSSpitChannel: start connectCopyJob0: ${it.hashCode()} $id $description")
                        connectCopyJob = it
                    },
                    rightProvider = {
                        logger.infoSync("WSSpitChannel: start connectCopyJob1: ${it.hashCode()} $id $description")
                        connectCopyJob1 = it
                    },
                    logger = logger,
                ) == StreamBridge.ReasonForStopping.LEFT
            }
        }
    */

    override val available: Available
        get() = currentMessage?.available ?: Available.UNKNOWN

    suspend fun awaitClose() {
        if (closed.getValue()) {
            return
        }
        suspendCancellableCoroutine {
            holder = it
        }
    }

    private fun closeHappened() {
        holder?.resume(Unit)
    }

    override suspend fun asyncClose() {
        if (closed.compareAndSet(false, true)) {
            closeHappened()
//            logger.info(text = "Closing...", exception = Throwable())
            connection.asyncCloseAnyway()
        }
    }

    override suspend fun flush() {
        // Do nothing
    }

    override suspend fun read(dest: ByteBuffer): DataTransferSize {
        return internalRead(dest)
    }

    private suspend fun internalRead(dest: ByteBuffer): DataTransferSize {
        while (true) {
            val current = currentMessage
            if (current != null) {
                val result = try {
                    current.read(dest)
                } catch (e: CancellationException) {
                    current.asyncClose()
                    currentMessage = null
                    logger.info("Reading data cancelled!: ${e.message}")
                    return DataTransferSize.EMPTY
                }
                if (result.isNotAvailable) {
                    currentMessage = null
                    current.asyncClose()
                    continue
                }
                if (current.available.isNotAvailable) {
                    current.asyncClose()
                    currentMessage = null
                }
                return result
            }
            val msg = try {
                val msg = connection.read()
                msg
            } catch (e: CancellationException) {
                // cancelled reading
                logger.info("Cancel reading: ${e.message}")
                return DataTransferSize.EMPTY
            } catch (e: WebSocketClosedException) {
                closeHappened()
                closed.setValue(true)
                return DataTransferSize.CLOSED
            } catch (e: Throwable) {
                logger.info(text = "Can't read next message. Exception happened.", exception = e)
                asyncClose()
                return DataTransferSize.CLOSED
            }
            when (msg.type) {
                MessageType.CLOSE -> {
                    logger.info("Was read close message")
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
                    val result = try {
                        msg.read(dest)
                    } catch (e: CancellationException) {
                        logger.info("Cancel reading 111: ${e.message}")
                        return DataTransferSize.EMPTY
                    }
                    if (result.isNotAvailable) {
                        currentMessage = null
                        continue
                    }
                    internalInput.addAndGet(result.length.toLong())
                    if (msg.available.isNotAvailable) {
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
//            logger.info("Send ${data.remaining} bytes")
            DataTransferSize.EMPTY
        } else {
//            logger.info("Send ${data.remaining} bytes")
//            logger.info("write ${data.toByteArray().toHexString()}")
            val w = try {
                connection.write(MessageType.BINARY).useAsync {
                    val e = DataTransferSize.ofSize(it.writeFully(data))
                    e
                }
            } catch (e: WebSocketClosedException) {
                asyncCloseAnyway()
                DataTransferSize.CLOSED
            }
            if (w.isAvailable) {
                internalOutput.addAndGet(w.length.toLong())
            }
            w
        }

}
