package pw.binom.proxy

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

class ProxyClientWebSocket(
    val connection: WebSocketConnection
) : ProxyClient {
    private val logger by Logger.ofThisOrGlobal
    private val cmdBuffer = ByteBuffer(DEFAULT_BUFFER_SIZE)
    private val eventBuffer = ByteBuffer(DEFAULT_BUFFER_SIZE)
    private val closed = AtomicBoolean(false)
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

    override suspend fun receiveCommand(): ControlRequestDto =
        try {
            connection.read().useAsync {
                val len = it.readInt(cmdBuffer)
                val data = it.readByteArray(len, cmdBuffer)
                Dto.decode(ControlRequestDto.serializer(), data)
            }
        } catch (e:WebSocketClosedException){
            throw ClosedException()
        }

    override suspend fun asyncClose() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        eventBuffer.close()
        cmdBuffer.close()
        connection.asyncCloseAnyway()
    }
}
