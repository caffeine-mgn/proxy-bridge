package pw.binom

import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import pw.binom.FrameChannelFrameReceiver.BufferFrameInput
import pw.binom.FrameChannelFrameSender.BufferFrameOutput
import pw.binom.frame.AbstractByteBufferFrameInput
import pw.binom.frame.AbstractByteBufferFrameOutput
import pw.binom.frame.FrameChannel
import pw.binom.frame.FrameInput
import pw.binom.frame.FrameOutput
import pw.binom.frame.FrameResult
import pw.binom.frame.PackageSize
import pw.binom.io.ByteBuffer
import pw.binom.io.use

class FrameChannelChannel(
    val income: ReceiveChannel<ByteBuffer>,
    val outcome: SendChannel<ByteBuffer>,
    override val bufferSize: PackageSize
) : FrameChannel {

    private class BufferFrameInput(override val buffer: ByteBuffer) : AbstractByteBufferFrameInput()
    private class BufferFrameOutput(override val buffer: ByteBuffer) : AbstractByteBufferFrameOutput()

    override suspend fun <T> readFrame(func: (FrameInput) -> T): FrameResult<T> {
        try {
            income.receive()
        } catch (_: ClosedReceiveChannelException) {
            return FrameResult.closed()
        }.use { buf ->
            return FrameResult.of(func(BufferFrameInput(buf)))
        }
    }

    override suspend fun asyncClose() {
        income.cancel()
        outcome.close()
    }

    override suspend fun <T> sendFrame(func: (FrameOutput) -> T): FrameResult<T> {
        val buffer = ByteBuffer(bufferSize.asInt)
        val result = func(BufferFrameOutput(buffer))
        try {
            buffer.flip()
            outcome.send(buffer)
        } catch (_: ClosedSendChannelException) {
            buffer.close()
            return FrameResult.closed()
        }
        return FrameResult.of(result)
    }
}
