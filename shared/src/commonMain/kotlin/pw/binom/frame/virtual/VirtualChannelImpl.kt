package pw.binom.frame.virtual

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import pw.binom.*
import pw.binom.atomic.AtomicBoolean
import pw.binom.frame.*
import pw.binom.io.ByteBuffer
import pw.binom.logger.Logger

class VirtualChannelImpl(
    override val id: ChannelId,
    bufferSize: PackageSize,
    val sender: FrameSender,
    val closeFunc: suspend (ChannelId) -> Unit,
) : VirtualChannel, AsyncReCloseable() {
    override val bufferSize: PackageSize = bufferSize - 1 - Short.SIZE_BYTES - 1
    private val incomeChannel = Channel<ByteBuffer>(onUndeliveredElement = { it?.close() })
    private var packageCounter = FrameId.INIT
    private val frameReorder = FrameReorder(channel = incomeChannel)


    suspend fun incomePackage(id: FrameId, data: ByteBuffer): Boolean {
        try {
            frameReorder.income(frame = id, data = data)
        } catch (e: ClosedSendChannelException) {
            data.close()
            return false
        }
        return true
    }


    override suspend fun <T> sendFrame(func: (FrameOutput) -> T): FrameResult<T> {
        if (isClosed) {
            return FrameResult.closed()
        }
        val frameId = packageCounter
        packageCounter = packageCounter.next

        val r = sender.sendFrame {
            it.writeByte(frameId.asByte)
            func(it)
        }
        if (r.isClosed) {
            asyncClose()
        }
        return r
    }

    private class BufferFrameInput(override val buffer: ByteBuffer) : AbstractByteBufferFrameInput()

    override suspend fun <T> readFrame(func: (FrameInput) -> T): FrameResult<T> {
        val data = try {
            incomeChannel.receive()
        } catch (_: ClosedReceiveChannelException) {
            return FrameResult.closed()
        } catch (_: CancellationException) {
            return FrameResult.closed()
        }
        return try {
            FrameResult.of(func(BufferFrameInput(data)))
        } finally {
            data.close()
        }
    }

    override suspend fun realAsyncClose() {
        incomeChannel.close()
        incomeChannel.close()
        closeFunc(id)
        frameReorder.asyncClose()
    }
}
