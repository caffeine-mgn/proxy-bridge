package pw.binom

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import pw.binom.io.AsyncCloseable
import pw.binom.io.ByteBuffer
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketClosedException
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.io.useAsync
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.network.SocketClosedException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class WebSocketProcessing(
    private val connection: WebSocketConnection,
    private val income: SendChannel<ByteBuffer>,
    private val outcome: ReceiveChannel<ByteBuffer>,
) : AsyncCloseable {
    private val buffer = ByteBuffer(100)
    private val buffer2 = ByteBuffer(100)
    private var writeJob: Job? = null
    private var readJob: Job? = null
    private val logger by Logger.ofThisOrGlobal

    suspend fun processing(context: CoroutineContext? = null) {
        val context = context ?: coroutineContext
        writeJob = GlobalScope.launch(context) {
            writingProcessing()
        }
        readJob = GlobalScope.launch(context) {
            readingProcessing()
        }
        joinAll(writeJob!!, readJob!!)
    }

    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun writingProcessing() {
        while (coroutineContext.isActive) {
            val buf = try {
                outcome.receive()
            } catch (_: ClosedReceiveChannelException) {
                break
            } catch (_: CancellationException) {
                break
            }
            try {
                connection.write(MessageType.BINARY).useAsync { msg ->
                    msg.writeInt(buf.remaining, buffer = buffer2)
                    logger.info("Outcome ${Int.SIZE_BYTES + buf.remaining} bytes")
                    msg.writeFully(buf)
                }
            } finally {
                buf.close()
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun readingProcessing() {
        while (coroutineContext.isActive) {
            val msg = try {
                connection.read()
            } catch (_: WebSocketClosedException) {
                return
            } catch (_: SocketClosedException) {
                return
            } catch (_: CancellationException) {
                return
            }
            when (msg.type) {
                MessageType.PING -> try {
                    msg.useAsync { income ->
                        connection.write(MessageType.PONG).useAsync { outcome ->
                            income.copyTo(outcome)
                        }
                    }
                } catch (_: WebSocketClosedException) {
                    return
                } catch (_: SocketClosedException) {
                    return
                } catch (_: CancellationException) {
                    return
                }

                else -> {
                    msg.useAsync { income ->
                        val size = income.readInt(buffer)
                        val buf = ByteBuffer(size)
                        income.readFully(buf)
                        buf.flip()
                        logger.info("Income ${size + Int.SIZE_BYTES} bytes")
                        try {
                            this.income.send(buf)
                        } catch (_: ClosedSendChannelException) {
                            buf.close()
                            return
                        } catch (_: CancellationException) {
                            buf.close()
                            return
                        } catch (e: Throwable) {
                            buf.close()
                            throw e
                        }
                    }
                }
            }
        }
    }

    override suspend fun asyncClose() {
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
