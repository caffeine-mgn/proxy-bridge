package pw.binom

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import pw.binom.atomic.AtomicBoolean
import pw.binom.frame.ByteBufferFrameInput
import pw.binom.frame.ByteBufferFrameOutput
import pw.binom.frame.FrameChannel
import pw.binom.frame.FrameInput
import pw.binom.frame.FrameOutput
import pw.binom.frame.FrameResult
import pw.binom.frame.PackageSize
import pw.binom.io.ByteArrayOutput
import pw.binom.io.ByteBuffer
import pw.binom.io.use

class TestingFrameChannel : FrameChannel {
    constructor(bufferSize: PackageSize = PackageSize(DEFAULT_BUFFER_SIZE)) {
        this.bufferSize = bufferSize
        data = Channel<FrameInput>(capacity = 100)
        data2 = Channel<FrameInput>(capacity = 100)
    }

    constructor(other: TestingFrameChannel) {
        this.bufferSize = other.bufferSize
        data = other.data2
        data2 = other.data
    }

    fun revert() = TestingFrameChannel(this)

    private val data: Channel<FrameInput>
    private val data2: Channel<FrameInput>

    override val bufferSize: PackageSize

    class ByteArrayOutputImpl : ByteArrayOutput() {
        operator fun ByteArray.unaryPlus() {
            write(this)
        }

        operator fun Byte.unaryPlus() {
            writeByte(this)
        }

        operator fun Int.unaryPlus() {
            writeInt(this)
        }

        operator fun ByteArray.not() {
            write(this)
        }

        operator fun Byte.not() {
            writeByte(this)
        }

        operator fun Int.not() {
            writeInt(this)
        }
    }

    fun pushInput(func: ByteArrayOutputImpl.() -> Unit) {
        val o = ByteArrayOutputImpl()
        func(o)
        pushInput(ByteBufferFrameInput(ByteBuffer.wrap(o.toByteArray())))
    }

    fun pushInput2(input: (FrameOutput) -> Unit) {
        val buf = ByteBuffer(bufferSize.asInt)
        input(ByteBufferFrameOutput(buf))
        buf.flip()
        pushInput(ByteBufferFrameInput(buf))
    }

    fun pushInput(input: FrameInput) {
        data.trySendBlocking(input).onFailure { throw IllegalStateException() }
    }

    suspend fun pop() = data2.receive()

    suspend fun popOut(): ByteArray {
        val e = data2.receive()
        return ByteBuffer(bufferSize.asInt).use { buf ->
            e.readInto(buf)
            buf.flip()
            buf.toByteArray()
        }
    }

    override suspend fun <T> sendFrame(func: (FrameOutput) -> T): FrameResult<T> {
        val buffer = ByteBuffer(bufferSize.asInt)
        val result2 = func(ByteBufferFrameOutput(buffer))
        buffer.flip()

        try {
            data2.send(ByteBufferFrameInput(buffer))
        } catch (_: ClosedSendChannelException) {
            return FrameResult.closed()
        }
        return FrameResult.of(result2)
    }

    override suspend fun <T> readFrame(func: (FrameInput) -> T): FrameResult<T> {
        val frameInput = try {
            data.receive()
        } catch (_: ClosedReceiveChannelException) {
            return FrameResult.closed()
        }
        return FrameResult.of(func(frameInput))
    }

    override suspend fun asyncClose() {
        data.close()
        data2.close()
    }
}
