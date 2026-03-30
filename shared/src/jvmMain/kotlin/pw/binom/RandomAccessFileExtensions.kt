package pw.binom

import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.readByteArray
import java.io.RandomAccessFile

private class RandomAccessFileSource(val file: RandomAccessFile) : RawSource {
    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        val bufferLen = minOf(byteCount, Int.MAX_VALUE.toLong()).toInt()
        val buffer = ByteArray(bufferLen)
        val len = minOf(byteCount, bufferLen.toLong()).toInt()
        val l = file.read(buffer, 0, len)
        if (l == -1) {
            return -1L
        }
//        repeat(l) {
//            val b = buffer[it]
//            println("R ${b.toUByte()} \"${b.toInt().toChar()}\"")
//        }
        sink.write(buffer, 0, l)
        return l.toLong()
    }

    override fun close() {
        file.close()
    }

}

private class RandomAccessFileSink(val file: RandomAccessFile) : RawSink {
    override fun write(source: Buffer, byteCount: Long) {
        var remaining = byteCount
        while (remaining > 0) {
            val len = minOf(remaining, Int.MAX_VALUE.toLong()).toInt()
            val data = source.readByteArray(len)
//            data.forEach {
//                println("W ${it.toUByte()} \"${it.toInt().toChar()}\"")
//            }
            file.write(data)
            remaining -= data.size
        }
    }

    override fun flush() {
    }

    override fun close() {
        file.close()
    }
}

fun RandomAccessFile.asSink(): RawSink = RandomAccessFileSink(this)
fun RandomAccessFile.asSource(): RawSource = RandomAccessFileSource(this)
