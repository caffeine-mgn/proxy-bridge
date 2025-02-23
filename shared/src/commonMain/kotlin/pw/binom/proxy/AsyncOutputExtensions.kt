package pw.binom.proxy

import pw.binom.*
import pw.binom.io.AsyncOutput
import pw.binom.io.ByteBuffer
import kotlin.jvm.JvmName

suspend fun AsyncOutput.flushPackage(buffer: ByteBuffer) {
    if (buffer.position > 0) {
        buffer.flip()
        writeFully(buffer)
        buffer.clear()
        flush()
    }
}

suspend fun AsyncOutput.flushPackageIfNeed(buffer: ByteBuffer, size: Int) {
    if (buffer.remaining <= size) {
        buffer.flip()
        writeFully(buffer)
        buffer.clear()
        flush()
    }
}

suspend fun AsyncOutput.writePackage(buffer: ByteBuffer, value: Int) {
    flushPackageIfNeed(buffer = buffer, size = Int.SIZE_BYTES)
    buffer.writeInt(value)
}

suspend fun AsyncOutput.writePackage(buffer: ByteBuffer, value: Short) {
    flushPackageIfNeed(buffer = buffer, size = Short.SIZE_BYTES)
    buffer.writeShort(value)
}

suspend fun AsyncOutput.writePackage(buffer: ByteBuffer, value: Byte) {
    flushPackageIfNeed(buffer = buffer, size = Byte.SIZE_BYTES)
    buffer.put(value)
}

@JvmName("writePackage2")
suspend fun AsyncOutput.writePackage(buffer: ByteBuffer, value: UByte) {
    writePackage(buffer, value.toByte())
}

suspend fun AsyncOutput.writePackage(buffer: ByteBuffer, value: ByteArray) {
    var offset = 0
    while (offset < value.size) {
        flushPackageIfNeed(buffer = buffer, size = value.size - offset)
        val wrote = buffer.write(value, offset = offset)
        offset += wrote.length
    }
}

suspend fun AsyncOutput.writePackage(buffer: ByteBuffer, value: String) {
    writePackage(buffer = buffer, value = value.length)
    writePackage(buffer = buffer, value = value.encodeToByteArray())
}
