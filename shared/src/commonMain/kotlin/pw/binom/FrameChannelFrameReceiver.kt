package pw.binom

import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import pw.binom.frame.AbstractByteBufferFrameInput
import pw.binom.frame.FrameInput
import pw.binom.frame.FrameReceiver
import pw.binom.frame.FrameResult
import pw.binom.frame.PackageSize
import pw.binom.io.ByteBuffer
import pw.binom.io.use

class FrameChannelFrameReceiver(
    private val channel: ReceiveChannel<ByteBuffer>,
    override val bufferSize: PackageSize
) : FrameReceiver {

    private class BufferFrameInput(override val buffer: ByteBuffer) : AbstractByteBufferFrameInput()

    override suspend fun <T> readFrame(func: (FrameInput) -> T): FrameResult<T> {
        try {
            channel.receive()
        } catch (e: ClosedReceiveChannelException) {
            return FrameResult.closed()
        }.use { buf ->
            return FrameResult.of(func(BufferFrameInput(buf)))
        }
    }

    override suspend fun asyncClose() {
        channel.cancel()
    }
}
