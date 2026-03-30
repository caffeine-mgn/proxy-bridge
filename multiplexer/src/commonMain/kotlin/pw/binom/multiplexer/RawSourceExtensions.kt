package pw.binom.multiplexer

import kotlinx.io.Buffer
import kotlinx.io.EOFException
import kotlinx.io.RawSource

fun RawSource.readFully(dst: Buffer, byteCount: Long) {
    var remaining = byteCount
    while (remaining > 0) {
        val len = readAtMostTo(dst, remaining)
        if (len == -1L) {
            throw EOFException("Can't read at most $remaining bytes")
        }
        remaining -= len
    }
}
