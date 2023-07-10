package pw.binom.proxy.io

import pw.binom.io.AsyncChannel
import pw.binom.io.AsyncOutput
import pw.binom.io.ByteBuffer
import pw.binom.io.http.websocket.Message
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketConnection

class AsyncInputViaWebSocketStream(private val connection: WebSocketConnection) : AsyncChannel {
    private var inputMessage: Message? = null
    override val available: Int
        get() = inputMessage?.available ?: -1

    override suspend fun asyncClose() {
        connection.asyncClose()
    }

    override suspend fun flush() {
        output?.flush()
    }

    override suspend fun read(dest: ByteBuffer): Int {
        var inputMessage = inputMessage
        if (inputMessage == null) {
            inputMessage = connection.read()
            this.inputMessage = inputMessage
        }
        val wasRead = inputMessage.read(dest)
        if (inputMessage.available == 0) {
            inputMessage.asyncClose()
            this.inputMessage = null
        }
        return wasRead
    }

    private var output: AsyncOutput? = null
    override suspend fun write(data: ByteBuffer): Int {
        var output = output
        if (output == null) {
            output = connection.write(MessageType.BINARY)
            this.output = output
        }
        return output.write(data)
    }
}
