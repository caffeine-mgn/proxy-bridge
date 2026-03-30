package pw.binom.utils

import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.readByteArray

fun RawSource.log(): RawSource = LogRawSource(this)

private class LogRawSource(val log: RawSource) : RawSource {
    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        val buffer = Buffer()
        val l = log.readAtMostTo(buffer, byteCount)
        val data = buffer.readByteArray()
        data.forEach { b ->
            println("R ${b.toUByte()} \"${b.toInt().toChar()}\"")
        }
        sink.write(data)
        return l
    }

    override fun close() {
        log.close()
    }
}
