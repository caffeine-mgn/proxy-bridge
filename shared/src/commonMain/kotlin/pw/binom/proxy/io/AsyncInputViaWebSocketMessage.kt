package pw.binom.proxy.io

import pw.binom.io.AsyncChannel
import pw.binom.io.ByteBuffer
import pw.binom.io.http.websocket.Message
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.io.use

class AsyncInputViaWebSocketMessage(private val connection: WebSocketConnection) : AsyncChannel {

    private var currentMessage: Message? = null

    override val available: Int
        get() = currentMessage?.available ?: -1

    override suspend fun asyncClose() {
        connection.asyncClose()
    }

    override suspend fun flush() {
        // Do nothing
    }

    override suspend fun read(dest: ByteBuffer): Int {
        var currentMessage = currentMessage
        if (currentMessage == null) {
            currentMessage = connection.read()
            this.currentMessage = currentMessage
        }
        val wasRead = currentMessage.read(dest)
        if (currentMessage.available == 0) {
            currentMessage.asyncClose()
            this.currentMessage = null
        }
        return wasRead
    }

    override suspend fun write(data: ByteBuffer): Int {
        val wrote = data.remaining
        connection.write(MessageType.BINARY).use { msg ->
            msg.writeFully(data)
        }
        return wrote
    }
}
