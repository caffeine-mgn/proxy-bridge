package pw.binom.sound

import pw.binom.ThreadCoroutineDispatcher
import pw.binom.atomic.AtomicBoolean
import pw.binom.io.AsyncOutput
import pw.binom.io.ByteBuffer
import pw.binom.io.DataTransferSize
import javax.sound.sampled.*


class AsyncSoundOutput(private val output: SourceDataLine) : AsyncOutput {
    companion object {

        fun create(format: AudioFormat, mixerInfo: Mixer.Info? = null): AsyncSoundOutput {
            val line = if (mixerInfo == null) {
                AudioSystem.getSourceDataLine(format)
            } else {
                // Получаем микшер (аудиоустройство) по его информации
                val mixer = AudioSystem.getMixer(mixerInfo)
                mixer.getLine(DataLine.Info(SourceDataLine::class.java, format)) as SourceDataLine
            }


            // Открываем линию для воспроизведения аудио на выбранном устройстве
            line.open(format)
            line.start()
            return AsyncSoundOutput(line)
        }
    }

    private val closed = AtomicBoolean(false)
    private val dispatcher = ThreadCoroutineDispatcher("")
    override suspend fun asyncClose() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        dispatcher.close()
        output.close()
    }

    override suspend fun flush() {
        // Do nothing
    }

    override suspend fun write(data: ByteBuffer): DataTransferSize {
        if (closed.getValue()) {
            return DataTransferSize.CLOSED
        }
        if (!data.hasRemaining) {
            return DataTransferSize.EMPTY
        }
        val bytes = data.toByteArray()
        dispatcher.asyncExecute {
            output.write(bytes, 0, bytes.size)
            output.drain()
        }
        return DataTransferSize.ofSize(bytes.size)
    }

    override suspend fun write(data: ByteArray, offset: Int, length: Int): DataTransferSize {
        if (closed.getValue()) {
            DataTransferSize.CLOSED
        }
        if (length <= 0 || data.size - offset <= length) {
            DataTransferSize.EMPTY
        }
        dispatcher.asyncExecute {
            output.write(data, offset, length)
            output.drain()
        }
        return DataTransferSize.ofSize(length)
    }
}
