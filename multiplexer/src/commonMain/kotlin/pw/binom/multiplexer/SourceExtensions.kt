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
}.let {
    if (it > UInt.MAX_VALUE.toULong())
        throw IllegalArgumentException("LEB128 value $it does not fit into UInt")
    it.toUInt()
}

fun Source.lebInt() = Leb.readSigned(maxBits = Int.SIZE_BITS) {
    readByte()
}.let {
    if (it < Int.MIN_VALUE.toLong() || it > Int.MAX_VALUE.toLong())
        throw IllegalArgumentException("LEB128 value $it does not fit into Int")
    it.toInt()
}


fun Sink.lebString(value: String) {
    val data = value.encodeToByteArray()
    lebInt(data.size)
    write(data)
}

fun Source.boolean() = readByte() == 1.toByte()
fun Sink.boolean(value: Boolean) {
    writeByte(if (value) 1 else 0)
}

inline fun <T> Sink.list(list: List<T>, func: (T) -> Unit) {
    lebInt(list.size)
    list.forEach(func)
}

inline fun <T> Source.list(func: (Source) -> T): List<T> {
    val size = lebInt()
    val result = ArrayList<T>(size)
    repeat(size) {
        result += func(this)
    }
    return result
}

fun <T> Sink.nullable(value: T?, func: Sink.(T) -> Unit) {
    if (value != null) {
        boolean(true)
        func(this, value)
    } else {
        boolean(false)
    }
}

inline fun <T> Source.nullable(func: (Source) -> T): T? =
    if (boolean()) {
        func(this)
    } else {
        null
    }

fun Source.lebString(): String {
    val size = lebInt()
    return readByteArray(size).decodeToString()
}
