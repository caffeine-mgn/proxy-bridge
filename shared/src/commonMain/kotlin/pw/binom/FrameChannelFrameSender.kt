package pw.binom

import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.SendChannel
import pw.binom.frame.AbstractByteBufferFrameOutput
import pw.binom.frame.FrameOutput
import pw.binom.frame.FrameResult
import pw.binom.frame.FrameSender
import pw.binom.frame.PackageSize
import pw.binom.io.ByteBuffer

class FrameChannelFrameSender(
    private val channel: SendChannel<ByteBuffer>,
    override val bufferSize: PackageSize,
) : FrameSender {
    private class BufferFrameOutput(override val buffer: ByteBuffer) : AbstractByteBufferFrameOutput()

    override suspend fun <T> sendFrame(func: (FrameOutput) -> T): FrameResult<T> {
        val buffer = ByteBuffer(bufferSize.asInt)
        val result = func(BufferFrameOutput(buffer))
        try {
            channel.send(buffer)
        } catch (_: ClosedSendChannelException) {
            buffer.close()
            return FrameResult.closed()
        }
        return FrameResult.of(result)
    }

    override suspend fun asyncClose() {
        channel.close()
    }
}
