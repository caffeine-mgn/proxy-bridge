package pw.binom.multiplexer

import kotlinx.io.Source

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
