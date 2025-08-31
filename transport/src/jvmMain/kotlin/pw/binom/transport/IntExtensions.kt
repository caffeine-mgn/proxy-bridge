package pw.binom.transport

import pw.binom.eachByteIndexed

//fun Int.Companion.fromBytes(byte0: Byte, byte1: Byte, byte2: Byte, byte3: Byte): Int =
//    ((byte0.toInt() and 0xFF) shl 24) +
//            ((byte1.toInt() and 0xFF) shl 16) +
//            ((byte2.toInt() and 0xFF) shl 8) +
//            ((byte3.toInt() and 0xFF) shl 0)
//
//fun Int.toByteArray(): ByteArray {
//    val output = ByteArray(Int.SIZE_BYTES)
//    eachByteIndexed { value, index ->
//        output[index] = value
//    }
//    return output
//}
