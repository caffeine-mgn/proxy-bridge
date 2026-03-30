package pw.binom.multiplexer

import kotlinx.io.Sink

fun Sink.lebUInt(value: UInt) = lebULong(value.toULong())
fun Sink.lebInt(value: Int) = lebLong(value.toLong())

fun Sink.lebULong(value: ULong) {
    Leb.writeUnsignedLeb1282(value = value) { byte ->
        writeByte(byte)
    }
}

fun Sink.lebLong(value: Long) {
    Leb.writeSignedLeb128(value = value) { byte ->
        writeByte(byte)
    }
}
