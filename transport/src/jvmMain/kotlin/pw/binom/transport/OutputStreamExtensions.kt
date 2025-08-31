package pw.binom.transport

import pw.binom.toByteArray
import java.io.OutputStream

fun OutputStream.writeInt(value: Int) {
    write(value.toByteArray())
}

fun OutputStream.writeString(value: String) {
    writeInt(value.length)
    write(value.encodeToByteArray())
}
