package pw.binom
/*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pw.binom.frame.AbstractByteBufferFrameInput
import pw.binom.frame.AbstractByteBufferFrameOutput
import pw.binom.frame.FrameOutput
import pw.binom.frame.FrameResult
import pw.binom.frame.PackageSize
import pw.binom.frame.virtual.VirtualChannel
import pw.binom.io.ByteBuffer
import pw.binom.io.empty
import pw.binom.io.holdState
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketClosedException
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.io.useAsync
import pw.binom.logger.info
import pw.binom.logger.warn
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

@Deprecated(message = "Not use it")
class WsVirtualChannelManager(
    private val connection: WebSocketConnection,
    override val bufferSize: PackageSize,
    private val channelProcessing: (id: ChannelId, channel: VirtualChannel) -> Unit = { _, _ -> }
) : AbstractVirtualChannelManager() {

    private val writeBuffer = ByteBuffer(bufferSize.asInt + Short.SIZE_BYTES)
    private val readBuffer = ByteBuffer(bufferSize.asInt + Short.SIZE_BYTES)
    private val wsReadChannel = Channel<Unit>(onUndeliveredElement = {})
    private val wsWriteChannel = Channel<Unit>(onUndeliveredElement = {})
    private val syncRead = AsyncSyncPoint()
    private val syncWrite = AsyncSyncPoint()

    private enum class ReadState {
        LOOP,
        READY_FOR_READ,
        CLOSE,
    }

    private val frameIn = object : AbstractByteBufferFrameInput() {
        override val buffer: ByteBuffer
            get() = readBuffer
    }

    private val frameOut = object : AbstractByteBufferFrameOutput() {
        override val buffer: ByteBuffer
            get() = writeBuffer
    }

    override suspend fun <T> pushFrame(func: (FrameOutput) -> T): FrameResult<T> {
        writeBuffer.clear()
        writeBuffer.writeShort(10)
        val result = func(frameOut)
        val size = (writeBuffer.position - Short.SIZE_BYTES).toUShort()
        writeBuffer.holdState {
            it.reset(0, Short.SIZE_BYTES)
            it.writeShort(size.toShort())
        }
        writeBuffer.flip()
        logger.info("Push for send ${writeBuffer.remaining} bytes")
        wsWriteChannel.send(Unit)
        syncWrite.lock()
        return FrameResult.Companion.of(result)
    }

    private suspend fun writingWsProcessing() {
        try {
            while (coroutineContext.isActive) {
                wsWriteChannel.receive()
                logger.info("Got ${writeBuffer.remaining} bytes for send to WS")
                connection.write(MessageType.BINARY).useAsync { out ->
                    out.writeFully(writeBuffer)
                }
                syncWrite.release()
            }
        } finally {
            wsWriteChannel.close()
        }
    }

    private suspend fun readingChannelProcessing() {
        try {
            while (coroutineContext.isActive) {
                if (!wsReadChannel.tryReceive().isFailure) {
                    try {
                        logger.info("Income package to channel ${readBuffer.remaining} bytes")
                        income(frameIn)
                    } finally {
                        syncRead.release()
                    }
                }
            }
        } finally {
            wsReadChannel.close()
        }
    }

    private suspend fun readingWsProcessing() {
        try {
            while (coroutineContext.isActive) {
                val msg = try {
                    logger.info("Try read message")
                    connection.read()
                } catch (_: WebSocketClosedException) {
                    break
                }
                logger.info("Message was read. Type: ${msg.type}")

                val sendPackage =
                    try {
                        msg.useAsync { msg ->
                            when (msg.type) {
                                MessageType.PING -> {
                                    connection.write(MessageType.PONG).useAsync { out ->
                                        msg.copyTo(out)
                                    }
                                    ReadState.LOOP
                                }

                                MessageType.CLOSE -> ReadState.CLOSE

                                else -> {
                                    val len = PackageSize(msg.readShort(readBuffer))
                                    logger.info("Was read from WebSocket $len")
                                    if (len.isZero) {
                                        readBuffer.empty()
                                    } else {
                                        readBuffer.reset(0, len.asInt)
                                        msg.readFully(readBuffer)
                                        readBuffer.flip()
                                    }
                                    ReadState.READY_FOR_READ
                                }
                            }
                        }
                    } catch (_: WebSocketClosedException) {
                        break
                    }

                when (sendPackage) {
                    ReadState.READY_FOR_READ -> {
                        logger.info("Was read from WebSocket ${readBuffer.remaining} bytes")
                        wsReadChannel.send(Unit)
                        syncRead.lock()
                        continue
                    }

                    ReadState.CLOSE -> break
                    ReadState.LOOP -> continue
                }
            }
        } finally {
            logger.info("Stop reading WebSocket")
            wsReadChannel.close()
        }
    }

    suspend fun processing(
        networkContext: CoroutineContext = EmptyCoroutineContext,
        channelContext: CoroutineContext = EmptyCoroutineContext,
    ) {
        logger.info("Process started!")
        try {
            val wsRead = GlobalScope.launch(networkContext) {
                readingWsProcessing()
            }
            val wsWrite = GlobalScope.launch(networkContext) {
                writingWsProcessing()
            }
            val channelRead = GlobalScope.launch(channelContext) {
                readingChannelProcessing()
            }

            try {
                wsRead.join()
            } catch (e: Throwable) {
                logger.warn(text = "Error on WebSocket reading", exception = e)
            }

            try {
                wsWrite.join()
            } catch (e: Throwable) {
                logger.warn(text = "Error on WebSocket writing", exception = e)
            }

            try {
                channelRead.cancelAndJoin()
            } catch (e: Throwable) {
                logger.warn(text = "Error on channel reading", exception = e)
            }
        } finally {
            logger.info("Process finished!")
        }
    }

    override suspend fun asyncClose() {
        try {
            super.asyncClose()
        } finally {
            connection.asyncClose()
        }
    }

    override fun emittedNewChannel(id: ChannelId, channel: VirtualChannel) {
        channelProcessing(id, channel)
    }
}
*/
