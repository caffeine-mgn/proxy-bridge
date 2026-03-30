package pw.binom.multiplexer

import kotlinx.io.Buffer
import kotlin.jvm.JvmName

@JvmName("bufferOf2")
fun bufferOf(bytes: ByteArray): Buffer {
    val buffer = Buffer()
    bytes.forEach {
        buffer.writeByte(it)
    }
    return buffer
}
fun bufferOf(vararg bytes: Byte): Buffer {
    val buffer = Buffer()
    bytes.forEach {
        buffer.writeByte(it)
    }
    return buffer
}
