package pw.binom.utils

import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource

fun RawSource.copyTo(dest: RawSink, bufferSize: Int = DEFAULT_BUFFER_SIZE): Long{
    val bufferField = Buffer()
    var totalBytesWritten: Long = 0
    while (readAtMostTo(bufferField, bufferSize.toLong()) != -1L) {
        val emitByteCount = bufferField.size
        if (emitByteCount > 0L) {
            totalBytesWritten += emitByteCount
            dest.write(bufferField, emitByteCount)
        }
    }
    if (bufferField.size > 0L) {
        totalBytesWritten += bufferField.size
        dest.write(bufferField, bufferField.size)
    }
    return totalBytesWritten
}

