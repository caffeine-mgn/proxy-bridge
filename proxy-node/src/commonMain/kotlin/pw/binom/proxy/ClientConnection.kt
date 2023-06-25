package pw.binom.proxy

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.*
import pw.binom.io.AsyncCloseable
import pw.binom.io.ByteBuffer
import pw.binom.io.Closeable
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketClosedException
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.io.use
import pw.binom.logger.Logger
import pw.binom.logger.warn
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.absoluteValue
import kotlin.random.Random

class ClientConnection(
    val connection: WebSocketConnection,
    val logger: Logger,
) : AsyncCloseable {
    suspend fun processing() {
        try {
            ByteBuffer(100).use { buffer ->
                while (true) {
                    connection.read().use { msg ->
                        val id = msg.readInt(buffer)
                        val success = msg.readByte(buffer)
                        val water = waiters.remove(id)
                        if (success > 0) {
                            water?.resume(Unit)
                        } else {
                            water?.resumeWithException(RuntimeException("Unsuccessful operation $id, success=$success"))
                        }
                    }
                }
            }
        } catch (e:WebSocketClosedException){
            logger.warn(exception = e, text = "Is Closed client connection = ${e.connection===connection}")
        }catch (e: Throwable) {
            logger.warn(exception = e)
        }
    }

    private var idCounter = 0
    private val waiters = HashMap<Int, CancellableContinuation<Unit>>()

    suspend fun connect(host: String, port: Int, channelId: Int): Int {
        val id = idCounter++
        connection.write(MessageType.BINARY).use { msg ->
            ByteBuffer(100).use { buffer ->
                msg.writeInt(id, buffer = buffer)
                msg.writeByte(Codes.CONNECT, buffer = buffer)
                msg.writeUTF8String(host, buffer = buffer)
                msg.writeShort(port.toShort(), buffer = buffer)
                msg.writeInt(channelId, buffer = buffer)
            }
        }
        println("Wrote success")
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
            it.cancel(kotlinx.coroutines.CancellationException("Closing ClientConnection"))
        }
        this.waiters.clear()
        connection.asyncCloseAnyway()
    }
}