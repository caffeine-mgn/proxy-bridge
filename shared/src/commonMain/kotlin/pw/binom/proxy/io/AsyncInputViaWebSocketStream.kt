package pw.binom.proxy.io

import pw.binom.io.*
import pw.binom.io.http.websocket.WebSocketInput
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketConnection

class AsyncInputViaWebSocketStream(private val connection: WebSocketConnection) : AsyncChannel {
    private var inputMessage: WebSocketInput? = null
    override val available: Available
        get() = inputMessage?.available ?: Available.UNKNOWN

    override suspend fun asyncClose() {
        connection.asyncClose()
    }

    override suspend fun flush() {
        output?.flush()
    }

    override suspend fun read(dest: ByteBuffer): DataTransferSize {
        var inputMessage = inputMessage
        if (inputMessage == null) {
            inputMessage = connection.read()
            this.inputMessage = inputMessage
        }
        val wasRead = inputMessage.read(dest)
        if (inputMessage.available.isNotAvailable) {
            inputMessage.asyncClose()
            this.inputMessage = null
        }
        return wasRead
    }

    private var output: AsyncOutput? = null
    override suspend fun write(data: ByteBuffer): DataTransferSize {
        var output = output
        if (output == null) {
            output = connection.write(MessageType.BINARY)
            this.output = output
        }
        return output.write(data)
    }
}
