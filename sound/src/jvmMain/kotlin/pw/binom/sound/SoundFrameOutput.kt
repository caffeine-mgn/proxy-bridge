package pw.binom.sound

import pw.binom.ThreadCoroutineDispatcher
import pw.binom.atomic.AtomicBoolean
import pw.binom.frame.*
import javax.sound.sampled.SourceDataLine

class SoundFrameOutput(private val output: SourceDataLine) : FrameSender {
    private val closed = AtomicBoolean(false)
    private val dispatcher = ThreadCoroutineDispatcher("")

    private val buffer = ByteArray(bufferSize.raw.toInt())
    private var cursor = 0

    private val frameOutput = object : FrameOutput {
        override fun writeByte(value: Byte) {
            buffer[cursor++] = value
        }
    }

    override suspend fun <T> sendFrame(func: (buffer: FrameOutput) -> T): FrameResult<T> {
        cursor = 0
        val result = FrameResult.of(func(frameOutput))
        output.write(buffer, 0, cursor)
        TODO("Not yet implemented")
    }

    override val bufferSize: PackageSize
        get() = TODO("Not yet implemented")

    private val intBuffer = ByteArray(Int.SIZE_BYTES)


    override suspend fun asyncClose() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        dispatcher.close()
        output.close()
    }
}
