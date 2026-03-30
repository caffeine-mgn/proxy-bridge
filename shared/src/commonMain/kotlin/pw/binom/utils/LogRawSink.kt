package pw.binom.utils

import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.readByteArray

fun RawSink.log(): RawSink = LogRawSink(this)

private class LogRawSink(val log: RawSink) : RawSink {
    override fun write(source: Buffer, byteCount: Long) {
        val data = source.readByteArray(byteCount.toInt())
        data.forEach {
            println("W ${it.toUByte()} \"${it.toInt().toChar()}\"")
        }
        val buffer = Buffer()
        buffer.write(data)
        log.write(buffer, buffer.size)
    }

    override fun flush() {
        log.flush()
    }

    override fun close() {
        log.close()
    }

}
