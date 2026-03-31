package pw.binom.multiplexer

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readByteArray

fun Source.lebULong() = Leb.readUnsigned(maxBits = Long.SIZE_BITS) {
    readByte()
}

fun Source.lebLong() = Leb.readSigned(maxBits = Long.SIZE_BITS) {
    readByte()
}

fun Source.lebUInt() = Leb.readUnsigned(maxBits = UInt.SIZE_BITS) {
    readByte()
}.toUInt()

fun Source.lebInt() = Leb.readSigned(maxBits = Int.SIZE_BITS) {
    readByte()
}.toInt()


fun Sink.lebString(value: String) {
    val data = value.encodeToByteArray()
    lebInt(data.size)
    write(data)
}

fun Source.lebString(): String {
    val size = lebInt()
    return readByteArray(size).decodeToString()
}
