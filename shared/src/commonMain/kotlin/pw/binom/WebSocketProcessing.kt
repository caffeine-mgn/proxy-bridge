package pw.binom

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.coroutines.AsyncReentrantLock
import pw.binom.io.AsyncCloseable
import pw.binom.io.AsyncOutput
import pw.binom.io.ByteBuffer
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketClosedException
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.io.http.websocket.WebSocketInput
import pw.binom.io.nextBytes
import pw.binom.io.use
import pw.binom.io.useAsync
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.warn
import pw.binom.network.SocketClosedException
import pw.binom.properties.PingProperties
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class WebSocketProcessing(
    private val connection: WebSocketConnection,
    private val income: SendChannel<ByteBuffer>,
    private val outcome: ReceiveChannel<ByteBuffer>,
    private val pingProperties: PingProperties,
) : AsyncCloseable {
    private val readBuffer = ByteBuffer(Int.SIZE_BYTES)
    private val writeBuffer = ByteBuffer(Int.SIZE_BYTES)
    private var writeJob: Job? = null
    private var readJob: Job? = null
    private val logger by Logger.ofThisOrGlobal
    private val sendLock = AsyncReentrantLock()


    suspend fun processing(context: CoroutineContext? = null) {
        val context = context ?: coroutineContext

//        val channelTransfer = GlobalScope.launch(context) {
//            try {
//                writeChannelProcessing()
//            } finally {
//                logger.info("stop channel transfer process")
//                this@WebSocketProcessing.readJob?.cancel()
//            }
//        }
        val writeJob = GlobalScope.launch(context) {
            try {
                writingProcessing()
            } finally {
                logger.info("stop writing process")
                this@WebSocketProcessing.readJob?.cancel()
            }
        }
        val readJob = GlobalScope.launch(context) {
            try {
                readingProcessing()
            } finally {
                logger.info("stop reading process")
                this@WebSocketProcessing.writeJob?.cancel()
            }
        }
//        this.pingJob = channelTransfer
        this.writeJob = writeJob
        this.readJob = readJob
        val pingResult = runCatching { sendingPing() }
        val readJobResult = runCatching {
            readJob.cancelAndJoin()
        }
        val writeJobResult = runCatching {
            writeJob.cancelAndJoin()
        }
        logger.info("readJobResult.isSuccess=${readJobResult.isSuccess}")
        logger.info("writeJobResult.isSuccess=${writeJobResult.isSuccess}")
        logger.info("pingResult.isSuccess=${pingResult.isSuccess}")
        if (readJobResult.isFailure) {
            logger.info("throw error #1")
            val e = readJobResult.exceptionOrNull()!!
            if (writeJobResult.isFailure) {
                logger.info("throw error #2")
                e.addSuppressed(writeJobResult.exceptionOrNull()!!)
            }
            throw e
        }
        if (writeJobResult.isFailure) {
            logger.info("throw error #3")
            throw writeJobResult.exceptionOrNull()!!
        }
    }

    private val pingLock = SpinLock()
    private var pingWaiter: CancellableContinuation<Unit>? = null
    private val SEND_TIMEOUT = 5.seconds

    private suspend fun sendingPing() {
        ByteBuffer(pingProperties.size).use { pingBuffer ->
            var failPing = 0
            while (coroutineContext.isActive) {
                delay(pingProperties.interval)
                try {
                    sendLock.synchronize(SEND_TIMEOUT) {
                        connection.write(MessageType.PING).useAsync { msg ->
                            pingBuffer.clear()
                            Random.nextBytes(pingBuffer)
                            pingBuffer.clear()
                            msg.writeFully(pingBuffer)
                            logger.info("Ping send success!")
                        }
                    }
                } catch (e: Throwable) {
                    logger.warn(text = "Can't send ping", exception = e)
                    break
                }

                val (isPingOk, latency) = measureTimedValue {
                    withTimeoutOrNull(pingProperties.timeout) {
                        suspendCancellableCoroutine {
                            pingLock.synchronize {
                                pingWaiter = it
                            }
                        }
                    } != null
                }
                if (isPingOk) {
                    logger.info("Ping response OK. latency=$latency")
                    failPing = 0
                } else {
                    if (failPing >= pingProperties.pingFailCount) {
                        logger.info("Ping timeout! No time for caution!")
                        return
                    }
                    logger.info("Ping timeout! Wait next ping fail")
                    failPing++
                }
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun writingProcessing() {
        while (coroutineContext.isActive) {
            val buf = try {
                outcome.receive()
            } catch (_: ClosedReceiveChannelException) {
                logger.warn(text = "Channel outcome closed!")
                break
            } catch (e: CancellationException) {
                logger.warn(text = "writing process cancelled!", exception = e)
                break
            }
            try {
                val dataForSend = buf.remaining
                logger.info("Try to send data $dataForSend")
                try {
                    sendLock.synchronize(lockingTimeout = SEND_TIMEOUT) {
                        connection.write(MessageType.BINARY).useAsync { msg ->
                            msg.writeInt(buf.remaining, buffer = writeBuffer)
                            logger.info("Outcome ${Int.SIZE_BYTES + buf.remaining} bytes")
                            msg.writeFully(buf)
                        }
                    }
                    logger.info("Data $dataForSend success sent!")
                } catch (e: Throwable) {
                    logger.info(text = "Can't send data to WS", exception = e)
                    asyncClose()
                    return
                }
            } finally {
                buf.close()
            }
        }
    }

    private suspend fun readDataMessage(msg: WebSocketInput): Boolean {
        val buf = msg.useAsync { income ->
            withTimeoutOrNull(5.seconds) {
                logger.info("Reading size....")
                val size = income.readInt(readBuffer)
                logger.info("Size was read: $size bytes. Try to read $size bytes")
                val buf = ByteBuffer(size)
                try {
                    income.readFully(buf)
                    logger.info("Bytes was read!")
                    buf.flip()
                    logger.info("Income ${size + Int.SIZE_BYTES} bytes")
                    buf
                } catch (e: Throwable) {
                    buf.close()
                    logger.warn(text = "Can't read data", exception = e)
                    throw e
                }
            }
        }
        if (buf == null) {
            logger.info("Can't read package: timeout. coroutineContext.isActive=${coroutineContext.isActive}")
            return true
        }
        try {
            logger.info("Try to send income ${buf.remaining} to channel")
            this.income.send(buf)
            logger.info("Sent income ${buf.remaining} to channel success")
        } catch (_: ClosedSendChannelException) {
            logger.warn("Can't read data: ClosedSendChannelException")
            buf.close()
            return false
        } catch (_: CancellationException) {
            logger.warn("Can't read data: CancellationException")
            buf.close()
            return false
        } catch (e: Throwable) {
            logger.warn(text = "Can't read data: Throwable", exception = e)
            buf.close()
            return false
        }
        return true
    }

    private suspend fun readPong(msg: WebSocketInput): Boolean {
        pingLock.synchronize {
            val p = pingWaiter
            pingWaiter = null
            p
        }?.resume(Unit)

        try {
            msg.useAsync {
                it.copyTo(AsyncOutput.NULL)
            }
        } catch (_: WebSocketClosedException) {
            logger.warn("Can't read pong: WebSocketClosedException")
            return false
        } catch (_: SocketClosedException) {
            logger.warn("Can't read pong: SocketClosedException")
            return false
        } catch (_: CancellationException) {
            logger.warn("Can't read pong: CancellationException")
            return false
        } catch (e: Throwable) {
            logger.warn(text = "Can't read pong: CancellationException", exception = e)
        }
        return true
    }

    private suspend fun readPing(msg: WebSocketInput): Boolean {
        try {
            val dataLen = msg.useAsync { income ->
                connection.write(MessageType.PONG).useAsync { outcome ->
                    income.copyTo(outcome)
                }
            }
            logger.info("Ping $dataLen bytes")
        } catch (_: WebSocketClosedException) {
            logger.warn("Can't send ping: WebSocketClosedException")
            return false
        } catch (_: SocketClosedException) {
            logger.warn("Can't send ping: SocketClosedException")
            return false
        } catch (_: CancellationException) {
            logger.warn("Can't send ping: CancellationException")
            return false
        } catch (e: Throwable) {
            logger.warn(text = "Can't send ping: CancellationException", exception = e)
        }
        return true
    }

    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun readingProcessing() {
        while (coroutineContext.isActive) {
            val msg = try {
                logger.info("Reading message...")
                connection.read()
            } catch (_: WebSocketClosedException) {
                logger.warn("Can't read message: WebSocketClosedException")
                return
            } catch (_: SocketClosedException) {
                logger.warn("Can't read message: SocketClosedException")
                return
            } catch (_: CancellationException) {
                logger.warn("Can't read message: CancellationException")
                return
            } catch (e: Throwable) {
                logger.warn(text = "Can't read message: CancellationException", exception = e)
                return
            }
            logger.info("Was read success type: ${msg.type}")
            when (msg.type) {
                MessageType.PING -> if (!readPing(msg)) {
                    logger.info("PING: Returns signal that reading should be stop!")
                    return
                }

                MessageType.PONG -> if (!readPong(msg)) {
                    logger.info("PING: Returns signal that reading should be stop!")
                    return
                }

                else -> if (!readDataMessage(msg)) {
                    logger.info("DATA: Returns signal that reading should be stop!")
                    return
                }
            }
        }
    }

    override suspend fun asyncClose() {
        logger.info(text = "Closing", exception = Throwable())
        try {
            try {
                writeJob?.cancelAndJoin()
            } finally {
                readJob?.cancelAndJoin()
            }
        } finally {
            connection.asyncCloseAnyway()
        }
    }
}
