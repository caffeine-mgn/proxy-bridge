package pw.binom

import pw.binom.io.AsyncOutput
import pw.binom.io.ByteBuffer
import pw.binom.io.DataTransferSize

/**
 * Эмулирует [AsyncOutput] под капотом разбивая сообщение на фреймы
 */
@Deprecated(message = "Not use it")
class FrameAsyncOutput(
    val bufferSize: Int = DEFAULT_BUFFER_SIZE,
    val func: suspend (ByteBuffer) -> Unit
) : AsyncOutput {
    private val buffer = ByteBuffer(bufferSize)
    override suspend fun asyncClose() {
        buffer.close()
    }

    override suspend fun flush() {
        if (buffer.position > 0) {
            buffer.flip()
            func(buffer)
            buffer.clear()
        }
    }

    override suspend fun write(data: ByteBuffer): DataTransferSize {
        var count = 0
        while (data.hasRemaining) {
            val l = data.write(buffer)
            if (l.isAvailable) {
                if (!buffer.hasRemaining) {
                    flush()
                }
                count += l.length
            }
        }
        return DataTransferSize.ofSize(count)
    }
}
