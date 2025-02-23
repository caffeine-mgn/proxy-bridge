package pw.binom.sound

import pw.binom.atomic.AtomicBoolean
import pw.binom.io.ByteBuffer
import pw.binom.io.DataTransferSize
import pw.binom.io.Output
import javax.sound.sampled.*

class SoundOutput(
    val output: SourceDataLine,
) : Output {
    companion object {
        fun create(format: AudioFormat): SoundOutput {
            val line = AudioSystem.getSourceDataLine(format)
            line.open(format)
            line.start()
            return SoundOutput(line)
        }

        fun create(format: AudioFormat, mixerInfo: Mixer.Info): SoundOutput {
            // Получаем микшер (аудиоустройство) по его информации
            val mixer = AudioSystem.getMixer(mixerInfo)

            // Открываем линию для воспроизведения аудио на выбранном устройстве
            val line = mixer.getLine(DataLine.Info(SourceDataLine::class.java, format)) as SourceDataLine
            line.open(format)
            line.start()
            return SoundOutput(line)
        }
    }

    private var closed = AtomicBoolean(false)
    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        output.close()
    }

    override fun flush() {
        // Do nothing
    }

    override fun write(data: ByteArray, offset: Int, length: Int): DataTransferSize {
        if (closed.getValue()) {
            return DataTransferSize.CLOSED
        }
        if (length <= 0) {
            return DataTransferSize.EMPTY
        }
        val wroteLen = output.write(data, length, length)
        output.drain()
        return DataTransferSize.ofSize(wroteLen)
    }

    override fun write(data: ByteBuffer): DataTransferSize {
        if (closed.getValue()) {
            return DataTransferSize.CLOSED
        }
        if (!data.hasRemaining) {
            return DataTransferSize.EMPTY
        }
        val bytes = data.toByteArray()
        output.write(bytes, 0, bytes.size)
        output.drain()
        return DataTransferSize.ofSize(bytes.size)
    }
}
