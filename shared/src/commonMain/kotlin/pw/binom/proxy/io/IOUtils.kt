package pw.binom.proxy.io

import kotlinx.coroutines.isActive
import pw.binom.DEFAULT_BUFFER_SIZE
import pw.binom.io.AsyncInput
import pw.binom.io.AsyncOutput
import pw.binom.io.ByteBuffer
import pw.binom.io.use
import kotlin.coroutines.coroutineContext

suspend fun AsyncInput.copyTo(
    dest: AsyncOutput,
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
    progress: suspend (Int) -> Unit,
): Long =
    ByteBuffer(bufferSize).use { buffer ->
        copyTo(
            dest = dest,
            buffer = buffer,
            progress = progress
        )
    }

suspend fun AsyncInput.copyTo(
    dest: AsyncOutput,
    buffer: ByteBuffer,
    progress: suspend (Int) -> Unit,
): Long {
    var totalLength = 0L
    while (coroutineContext.isActive) {
        buffer.clear()
//        println("reading data from $this. bufferSize: ${buffer.remaining}")
        val length = read(buffer)
        if (length.isNotAvailable) {
            break
        }
        progress(length.length)
        totalLength += length.length.toLong()
        buffer.flip()
//        val data = buffer.toByteArray().map { it.toUByte().toString().padStart(2, '0') }
//            .joinToString()
//        println("data=$data")

        val l = dest.writeFully(buffer)
//        println("wroted $l to $dest")
        dest.flush()
    }
    return totalLength
}
