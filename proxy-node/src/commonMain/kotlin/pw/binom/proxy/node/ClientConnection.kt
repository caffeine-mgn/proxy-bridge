package pw.binom.proxy.node

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.*
import pw.binom.io.AsyncCloseable
import pw.binom.io.AsyncInput
import pw.binom.io.ByteBuffer
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketClosedException
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.io.socket.UnknownHostException
import pw.binom.io.use
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.warn
import pw.binom.proxy.Codes
import pw.binom.proxy.ControlResponseCodes
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ClientConnection(
    val connection: WebSocketConnection,
    val logger: Logger,
) : AsyncCloseable {
    suspend fun processing() {
        try {
            ByteBuffer(100).use { buffer ->
                while (true) {
                    connection.read().use { msg ->
                        logger.info("Reading message ${msg.hashCode()}")
                        try {
                            val id = msg.readInt(buffer)
                            val success = msg.readByte(buffer)
                            val water = waiters.remove(id)
                            when (success) {
                                ControlResponseCodes.OK.code -> water?.resume(Unit)
                                ControlResponseCodes.UNKNOWN_HOST.code -> water?.resumeWithException(
                                    UnknownHostException()
                                )

                                ControlResponseCodes.UNKNOWN_ERROR.code -> water?.resumeWithException(RuntimeException("Unknown error $success. Operation $id"))
                                else -> water?.resumeWithException(RuntimeException("Unsuccessful operation $id, success=$success"))
                            }
                        } catch (e: Throwable) {

                        }
                    }
                }
            }
        } catch (e: WebSocketClosedException) {
            if (e.connection !== connection) {
                logger.warn(exception = e)
            }
        } catch (e: Throwable) {
            logger.warn(exception = e, text = "Error on processing")
        } finally {
            logger.info("Connection finished!")
        }
    }

    private var idCounter = 0
    private val waiters = HashMap<Int, CancellableContinuation<Unit>>()

    suspend fun putFile(path: String, file: AsyncInput) {
        val id = idCounter++
        connection.write(MessageType.BINARY).use { msg ->
            ByteBuffer(100).use { buffer ->
                msg.writeInt(id, buffer = buffer)
                msg.writeByte(Codes.PUT_FILE, buffer = buffer)
                msg.writeUTF8String(path, buffer = buffer)
                file.copyTo(msg)
            }
        }
        suspendCancellableCoroutine {
            it.invokeOnCancellation {
                waiters.remove(id)
            }
            waiters[id] = it
        }
    }

    suspend fun connect(host: String, port: Int, channelId: Int): Int {
        val id = idCounter++
        connection.write(MessageType.BINARY).use { msg ->
            logger.info("Start send connect message")
            ByteBuffer(100).use { buffer ->
                msg.writeInt(id, buffer = buffer)
                msg.writeByte(Codes.CONNECT, buffer = buffer)
                msg.writeUTF8String(host, buffer = buffer)
                msg.writeShort(port.toShort(), buffer = buffer)
                msg.writeInt(channelId, buffer = buffer)
            }
            logger.info("Connect message sent!")
        }
        suspendCancellableCoroutine {
            it.invokeOnCancellation {
                waiters.remove(id)
            }
            waiters[id] = it
        }
        return channelId
    }

    override suspend fun asyncClose() {
        this.waiters.values.forEach {
            println("ClientConnection:: Closing ClientConnection")
            it.cancel(kotlinx.coroutines.CancellationException("Closing ClientConnection"))
        }
        this.waiters.clear()
        connection.asyncCloseAnyway()
    }
}
