package pw.binom

val ULong.compressedSize: Int
    get() = when {
        this < 128uL -> 1
        this < 256uL -> 2
        this < 65536uL -> 3
        this < 4294967296uL -> 5
        else -> 9
    }

inline fun ULong.encodeVL(push: (Byte) -> Unit) {
    when {
        this < 128uL -> push(this.toByte()) // 1 байт
        this < 256uL -> {
            push(0xCC.toByte())
            push(toByte())
        }  // 2 байта
        this < 65536uL -> {
            push(0xCD.toByte())
            toShort().eachByteIndexed { byte, _ ->
                push(byte)
            }
        } // 3 байта

        this < 4294967296uL -> {
            push(0xCE.toByte())
            toInt().eachByteIndexed { byte, _ ->
                push(byte)
            }
        } // 5 байт

        else -> {
            push(0xCF.toByte())
            toLong().eachByteIndexed { byte, _ ->
                push(byte)
            }
        } // 9 байт
    }
}

inline fun ULong.Companion.decodeVL(bytes: () -> Byte): ULong {
    val v0 = bytes()
    return when (v0.toInt() and 0xFF) {
        in 0..0x7F -> v0.toLong() and 0xFF  // Один байт
        0xCC -> bytes().toLong() and 0xFF  // Два байта
        0xCD -> ((bytes().toLong() and 0xFF) shl 8) or (bytes().toInt() and 0xFF).toLong()  // Три байта
        0xCE -> ((bytes().toLong() and 0xFF) shl 24) or
                ((bytes().toLong() and 0xFF) shl 16) or
                ((bytes().toLong() and 0xFF) shl 8) or
                (bytes().toLong() and 0xFF)  // Пять байтов
        0xCF -> ((bytes().toLong() and 0xFF) shl 56) or
                ((bytes().toLong() and 0xFF) shl 48) or
                ((bytes().toLong() and 0xFF) shl 40) or
                ((bytes().toLong() and 0xFF) shl 32) or
                ((bytes().toLong() and 0xFF) shl 24) or
                ((bytes().toLong() and 0xFF) shl 16) or
                ((bytes().toLong() and 0xFF) shl 8) or
                (bytes().toLong() and 0xFF)  // Девять байтов
        else -> throw IllegalArgumentException("Invalid MsgPack unsigned integer format")
    }.toULong()
}

/*




fun Short.toBytes(): ByteArray = byteArrayOf((this.toInt() shr 8).toByte(), this.toByte())
fun Int.toBytes(): ByteArray = byteArrayOf(
    (this shr 24).toByte(),
    (this shr 16).toByte(),
    (this shr 8).toByte(),
    this.toByte()
)
fun Long.toBytes(): ByteArray = byteArrayOf(
    (this shr 56).toByte(),
    (this shr 48).toByte(),
    (this shr 40).toByte(),
    (this shr 32).toByte(),
    (this shr 24).toByte(),
    (this shr 16).toByte(),
    (this shr 8).toByte(),
    this.toByte()
)
*/
