package pw.binom.proxy

import pw.binom.Dto
import pw.binom.frame.FrameChannel
import pw.binom.io.ClosedException
import pw.binom.proxy.dto.ControlEventDto
import pw.binom.proxy.dto.ControlRequestDto

class FrameProxyClient(val channel: FrameChannel) : ProxyClient {
    override suspend fun sendEvent(event: ControlEventDto) {
        val data = Dto.encode(ControlEventDto.serializer(), event)
        val result = channel.sendFrame { buffer ->
            buffer.writeInt(data.size)
            buffer.writeByteArray(data)
        }
        if (result.isClosed) {
            throw ClosedException()
        }
    }

    override suspend fun receiveCommand(): ControlRequestDto {
        val result = channel.readFrame { buffer ->
            val size = buffer.readInt()
            val data = buffer.readByteArray(size)
            Dto.decode(ControlRequestDto.serializer(), data)
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
