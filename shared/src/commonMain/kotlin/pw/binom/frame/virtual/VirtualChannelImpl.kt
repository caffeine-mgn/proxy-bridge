package pw.binom.frame.virtual

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import pw.binom.*
import pw.binom.atomic.AtomicBoolean
import pw.binom.frame.*
import pw.binom.io.ByteBuffer

class VirtualChannelImpl(
    override val id: ChannelId,
    bufferSize: PackageSize,
    val sender: FrameSender,
    val closeFunc: suspend (ChannelId) -> Unit,
    val disposeFunc: suspend (ChannelId) -> Unit,

    ) : VirtualChannel {
    override val bufferSize: PackageSize = bufferSize - 1 - Short.SIZE_BYTES - 1
    private val incomeChannel = Channel<ByteBuffer?>(onUndeliveredElement = { it?.close() })
    private var packageCounter = FrameId.INIT
    private val frameReorder = FrameReorder(
        channel = incomeChannel,
        closeFunc = { it?.close() },
    )


    suspend fun incomePackage(id: FrameId, data: ByteBuffer): Boolean {
        try {
//            SlowCoroutineDetect.detect("VirtualChannelImpl long income processing") {
            frameReorder.income(frame = id, data = data)
//            }
        } catch (e: ClosedSendChannelException) {
            data.close()
            return false
        }
        return true
    }

    private val closed = AtomicBoolean(false)

    override suspend fun <T> sendFrame(func: (FrameOutput) -> T): FrameResult<T> {
        if (closed.getValue()) {
            return FrameResult.closed()
        }
        val frameId = packageCounter
        packageCounter = packageCounter.next

        val r = SlowCoroutineDetect.detect("VirtualChannelImpl long waring write outcome message") {
            sender.sendFrame {
                it.writeByte(frameId.asByte)
                func(it)
            }
        }
        if (r.isClosed) {
            asyncClose()
        }
        return r
    }

    private class BufferFrameInput(override val buffer: ByteBuffer) : AbstractByteBufferFrameInput()

    override suspend fun <T> readFrame(func: (FrameInput) -> T): FrameResult<T> {
        val data = try {
//            SlowCoroutineDetect.detect("VirtualChannelImpl long waiting income message") {
            incomeChannel.receive()
//            }
        } catch (_: ClosedReceiveChannelException) {
            return FrameResult.closed()
        } catch (_: CancellationException) {
            return FrameResult.closed()
        }
        if (data == null) {
            asyncClose()
            return FrameResult.closed()
        }
        return try {
            FrameResult.of(func(BufferFrameInput(data)))
        } finally {
            data.close()
        }
    }

    suspend fun closeReceived() {
        runCatching {
            incomeChannel.send(null)
        }
    }

    override suspend fun asyncClose() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        try {
            incomeChannel.close()
            frameReorder.asyncClose()
        } finally {
            disposeFunc(id)
            closeFunc(id)
        }
    }

}
