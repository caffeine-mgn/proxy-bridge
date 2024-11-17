package pw.binom.gateway
/*

import pw.binom.*
import pw.binom.atomic.AtomicBoolean
import pw.binom.io.*
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketClosedException
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.Dto
import pw.binom.proxy.dto.ControlEventDto
import pw.binom.proxy.dto.ControlRequestDto

class GatewayClientWebSocket(val connection: WebSocketConnection) : GatewayClient {
    private val logger by Logger.ofThisOrGlobal
    private val buffer = ByteBuffer(1024 * 2)
    private val closed = AtomicBoolean(false)
    override suspend fun asyncClose() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        buffer.close()
        connection.asyncCloseAnyway()
    }

    override suspend fun sendCmd(request: ControlRequestDto) {
        try {
            val data = Dto.encode(
                ControlRequestDto.serializer(),
                request
            )
            val connection = connection
            connection
                .write(MessageType.BINARY).useAsync {
                    it.writeInt(data.size, buffer)
                    it.writeByteArray(data, buffer)
                }
        } catch (e: Throwable) {
            throw RuntimeException("Can't send cmd $request", e)
        }
    }

    override suspend fun receiveEvent(): ControlEventDto {
        try {
            while (true) {
                val msg = connection.read()
                when (msg.type) {
                    MessageType.PING -> msg.useAsync { input ->
                        connection.write(MessageType.PONG).useAsync { output ->
                            input.copyTo(output)
                        }
                    }

                    MessageType.CLOSE -> {
                        msg.asyncClose()
                        throw ClosedException()
                    }

                    else -> {
                        return msg.useAsync {
                            val packageSize = it.readInt(buffer)
                            val data = it.readBytes(packageSize)
                            Dto.decode(ControlEventDto.serializer(), data)
                        }
                    }
                }

            }
        } catch (e: ClosedException) {
            throw e
        } catch (e: WebSocketClosedException) {
            logger.info("Control channel closed")
            throw ClosedException()
        } catch (e: Throwable) {
            logger.info("Error on control processing")
            throw e
        }
    }
}
*/
