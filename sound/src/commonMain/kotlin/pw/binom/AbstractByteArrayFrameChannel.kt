package pw.binom

import pw.binom.frame.FrameChannel
import pw.binom.frame.FrameInput
import pw.binom.frame.FrameOutput
import pw.binom.frame.FrameResult

abstract class AbstractByteArrayFrameChannel : FrameChannel {
    val readBuffer = ByteArray(bufferSize.raw.toInt())
    val sendBuffer = ByteArray(bufferSize.raw.toInt())
    val writeSizeBuffer = ByteArray(Int.SIZE_BYTES)

    init {
        require(bufferSize.raw.toInt() % 4 == 0) { "bufferSize ($bufferSize) must be a multiple of 4." }
    }

    private var writeCursor = 0
    private var readCursor = 0
    private var frameSize = 0

    private val frameOutput = object : FrameOutput {
        override fun writeByte(value: Byte) {
            sendBuffer[writeCursor++] = value
        }
    }

    private val frameInput = object : FrameInput {
        override fun readByte(): Byte {
            // TODO добавить ограничение
            return readBuffer[readCursor++]
        }

    }

    override suspend fun <T> readFrame(func: (buffer: FrameInput) -> T): FrameResult<T> {
        readByteArray(readBuffer, Int.SIZE_BYTES)
        readBuffer
        val size = Int.fromBytes(readBuffer, 0)
        frameSize = size
        val fullSize = size + size % Int.SIZE_BYTES
        readCursor = 0
        readByteArray(readBuffer, fullSize)
        return FrameResult.of(func(frameInput))
    }

    override suspend fun <T> sendFrame(func: (buffer: FrameOutput) -> T): FrameResult<T> {
        writeCursor = 0
        val result = func(frameOutput)
        if (writeCursor <= 0) {
            return FrameResult.of(result)
        }
        writeCursor.toByteArray(writeSizeBuffer)
        writeByteArray(writeSizeBuffer, Int.SIZE_BYTES)
        repeat(writeCursor % Int.SIZE_BYTES) {
            sendBuffer[writeCursor++] = 0
        }
        writeByteArray(sendBuffer, writeCursor)
        return FrameResult.of(result)
    }

    protected abstract fun writeByteArray(data: ByteArray, size: Int)
    protected abstract fun readByteArray(data: ByteArray, size: Int)
}
