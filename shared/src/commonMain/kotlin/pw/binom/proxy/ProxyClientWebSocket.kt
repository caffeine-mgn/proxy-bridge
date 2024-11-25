package pw.binom.proxy

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import pw.binom.*
import pw.binom.atomic.AtomicBoolean
import pw.binom.io.ByteBuffer
import pw.binom.io.ClosedException
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketClosedException
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.io.useAsync
import pw.binom.io.writeByteArray
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.proxy.dto.ControlEventDto
import pw.binom.proxy.dto.ControlRequestDto
import kotlin.coroutines.resume
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.measureTime

class ProxyClientWebSocket(
    val connection: WebSocketConnection
) : ProxyClient {
    private val logger by Logger.ofThisOrGlobal
    private val cmdBuffer = byteBuffer(DEFAULT_BUFFER_SIZE)
    private val eventBuffer = byteBuffer(DEFAULT_BUFFER_SIZE)
    private val pingBuffer = byteBuffer(8)
    private val closed = AtomicBoolean(false)
    private var pongWaiter: CancellableContinuation<Unit>? = null

    private val pingJob = GlobalScope.launch {
        while (coroutineContext.isActive) {
            delay(10.seconds)
            val pingStart = TimeSource.Monotonic.markNow()
            connection.write(MessageType.PING).useAsync {
                it.writeInt(Random.nextInt(), pingBuffer)
            }
            val isOk = withTimeoutOrNull(10.seconds) {
                suspendCancellableCoroutine<Unit> { pongWaiter = it }
            } != null
            if (!isOk) {
                logger.info("Ping timeout!")
                asyncClose()
            } else {
//                logger.info("Ping OK. timing: ${pingStart.elapsedNow()}")
            }
        }
    }

    override suspend fun sendEvent(event: ControlEventDto) {
        try {
            logger.info("Send event $event")
            val data = Dto.encode(ControlEventDto.serializer(), event)
            val connect = connection
            connect.write(MessageType.BINARY).useAsync {
                it.writeInt(data.size, eventBuffer)
                it.writeByteArray(data, eventBuffer)
            }
        } catch (e: Throwable) {
            throw RuntimeException("Can't send event $event", e)
        }
    }

    override suspend fun receiveCommand(): ControlRequestDto {
        try {
            while (true) {
                val msg = connection.read()
                when (msg.type) {
                    MessageType.PING -> msg.useAsync { input ->
                        connection.write(MessageType.PONG).useAsync { output ->
                            input.copyTo(output)
                        }
                    }

                    MessageType.PONG -> {
                        msg.asyncClose()
                        pongWaiter?.resume(Unit)
                        pongWaiter = null
                    }

                    MessageType.CLOSE -> {
                        logger.info("Received close command")
                        msg.asyncClose()
                        asyncClose()
                        throw ClosedException()
                    }

                    else -> return msg.useAsync {
                        val len = it.readInt(cmdBuffer)
                        val data = it.readByteArray(len, cmdBuffer)
                        Dto.decode(ControlRequestDto.serializer(), data)
                    }

                }

            }
        } catch (e: ClosedException) {
            asyncClose()
            throw e
        } catch (e: WebSocketClosedException) {
            e.printStackTrace()
            asyncClose()
            throw ClosedException()
        }
    }

    override suspend fun asyncClose() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        pingJob.cancel()
        eventBuffer.close()
        cmdBuffer.close()
        pingBuffer.close()
        connection.asyncCloseAnyway()
    }
}
