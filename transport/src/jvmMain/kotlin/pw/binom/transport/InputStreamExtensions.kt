package pw.binom.transport

import pw.binom.fromBytes
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

fun InputStream.readFully(dest: ByteArray, offset: Int = 0, length: Int = dest.size - offset) {
    var wasRead = 0
    while (true) {
        val r = read(dest, offset + wasRead, length - wasRead)
        if (r > 0) {
            wasRead += r
            if (wasRead == length) {
                return
            } else {
                continue
            }
        }
        if (wasRead > 0) {
            throw IllegalStateException("PackageBreak")
        } else {
            throw EOFException()
        }
    }
}

fun InputStream.copyTo2(dest: OutputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE, progress: (Long) -> Unit): Long {
    var bytesCopied = 0L;
    val buffer = ByteArray(bufferSize)

    while (true) {
        val bytes = read(buffer)
        if (bytes < 0) {
            break
        }
        bytesCopied += bytes
        dest.write(buffer, 0, bytes)
        progress(bytesCopied)
    }
    return bytesCopied
}

fun InputStream.readInt(): Int {
    val e = ByteArray(4)
    readFully(e)
    return Int.fromBytes(e[0], e[1], e[2], e[3])
}

fun InputStream.readString(): String {
    val size = readInt()
    val bytes = ByteArray(size)
    readFully(bytes)
    return bytes.decodeToString()
}
