package pw.binom.sound

import pw.binom.ThreadCoroutineDispatcher
import pw.binom.atomic.AtomicBoolean
import pw.binom.io.AsyncInput
import pw.binom.io.Available
import pw.binom.io.ByteBuffer
import pw.binom.io.DataTransferSize
import javax.sound.sampled.*


class AsyncSoundInput(private val line: TargetDataLine) : AsyncInput {
    companion object {
        fun create(format: AudioFormat, mixerInfo: Mixer.Info? = null): AsyncSoundInput {
            val microphone = if (mixerInfo == null) {
                val info = DataLine.Info(TargetDataLine::class.java, format)
                AudioSystem.getLine(info) as TargetDataLine
            } else {
                // Получаем микшер (аудиоустройство) по его информации
                val mixer = AudioSystem.getMixer(mixerInfo)
                mixer.getLine(DataLine.Info(TargetDataLine::class.java, format)) as TargetDataLine
            }

            microphone.open(format)
            microphone.start()
            return AsyncSoundInput(microphone)
        }
    }

    private val closed = AtomicBoolean(false)
    private val dispatcher = ThreadCoroutineDispatcher("")
    override val available: Available
        get() = Available.UNKNOWN

    override suspend fun asyncClose() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        dispatcher.close()
        line.close()
    }

    override suspend fun read(dest: ByteBuffer): DataTransferSize {
        val byteArray = ByteArray(dest.remaining)
        val l = read(byteArray)
        if (l.isNotAvailable) {
            return l
        }
        dest.write(data = byteArray, offset = 0, length = l.length)
        return l
    }

    override suspend fun read(dest: ByteArray, offset: Int, length: Int): DataTransferSize {
        val available = line.available()
        if (available > 0) {
            return DataTransferSize.ofSize(line.read(dest, offset, minOf(available, length)))
        }
        dispatcher.asyncExecute {
            while (!closed.getValue() && line.available() == 0) {
                Thread.yield()
            }
        }
        if (closed.getValue()) {
            DataTransferSize.CLOSED
        }
        return DataTransferSize.ofSize(line.read(dest, offset, minOf(available, length)))
    }
}
