package pw.binom.gateway
/*

import pw.binom.Dto
import pw.binom.frame.FrameChannel
import pw.binom.io.ClosedException
import pw.binom.proxy.dto.ControlEventDto
import pw.binom.proxy.dto.ControlRequestDto

class FrameGatewayClient(val channel: FrameChannel) : GatewayClient {

    override suspend fun sendCmd(request: ControlRequestDto) {
        val data = Dto.encode(
            ControlRequestDto.serializer(),
            request
        )
        val result = channel.sendFrame { buffer ->
            buffer.writeInt(data.size)
            buffer.writeByteArray(data)
        }
        if (result.isClosed) {
            throw ClosedException()
        }
    }

    override suspend fun receiveEvent(): ControlEventDto {
        val result = channel.readFrame { buffer ->
            val size = buffer.readInt()
            val data = buffer.readByteArray(size)
            Dto.decode(ControlEventDto.serializer(), data)
        }
        if (result.isClosed) {
            throw ClosedException()
        }
        return result.getOrThrow()
    }

    override suspend fun asyncClose() {
        channel.asyncClose()
    }
}
*/
