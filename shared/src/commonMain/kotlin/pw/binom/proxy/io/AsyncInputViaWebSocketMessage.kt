package pw.binom.proxy.io

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.*
import pw.binom.atomic.AtomicBoolean
import pw.binom.atomic.AtomicLong
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.*
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.io.http.websocket.WebSocketInput
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.TimeSource

class AsyncInputViaWebSocketMessage(private val connection: WebSocketConnection) : AsyncChannel {

    private var currentMessage: WebSocketInput? = null

    companion object {
        private const val MAX_PING_BYTES = Long.SIZE_BYTES
    }

    override val available: Available
        get() = currentMessage?.available ?: Available.UNKNOWN

    override suspend fun asyncClose() {
        connection.asyncClose()
    }

    override suspend fun flush() {
        // Do nothing
    }

    private var connectionClosed = AtomicBoolean(false)

    private val pingWaitersLock = SpinLock()
    private val pingWaiters = HashMap<Long, CancellableContinuation<Unit>>()
    private var pingCounter = AtomicLong(0)

    suspend fun ping(): Duration {
        val now = TimeSource.Monotonic.markNow()
        val pingId = pingCounter.addAndGet(1)
        connection.write(MessageType.PING).useAsync { out ->
            byteBuffer(MAX_PING_BYTES).use { buffer ->
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
        return now.elapsedNow()
    }

    override suspend fun read(dest: ByteBuffer): DataTransferSize {
        while (true) {
            var currentMessage = currentMessage
            if (currentMessage == null) {
                if (connectionClosed.getValue()) {
                    connection.asyncCloseAnyway()
                    return DataTransferSize.CLOSED
                }
                val msg = connection.read()
                if (msg.isCloseMessage) {
                    connectionClosed.setValue(true)
                    continue
                }
                if (msg.type == MessageType.PING) {
                    connection.write(MessageType.PONG).useAsync { out ->
                        byteBuffer(MAX_PING_BYTES).use { buffer ->
                            buffer.reset(position = 0, length = MAX_PING_BYTES)
                            msg.readFully(buffer)
                            buffer.flip()
                            out.writeFully(buffer)
                        }
                    }
                    continue
                }
                if (msg.type == MessageType.PONG) {
                    byteBuffer(MAX_PING_BYTES).use { buffer ->
                        msg.readFully(buffer)
                        buffer.flip()
                        if (buffer.remaining != MAX_PING_BYTES) {
                            val pingId = buffer.readLong()
                            pingWaitersLock.synchronize {
                                pingWaiters.remove(pingId)?.resume(Unit)
                            }
                        }
                    }
                    continue
                }
                if (msg.type != MessageType.BINARY && msg.type != MessageType.TEXT) {
                    continue
                }
                currentMessage = msg
                this.currentMessage = currentMessage
            }
            val wasRead = currentMessage.read(dest)
            if (currentMessage.available.isNotAvailable) {
                currentMessage.asyncClose()
                this.currentMessage = null
            }
            return wasRead
        }
    }

    override suspend fun write(data: ByteBuffer): DataTransferSize {
        val wrote = data.remaining
        connection.write(MessageType.BINARY).useAsync { msg ->
            msg.writeFully(data)
        }
        return DataTransferSize.ofSize(wrote)
    }
}
