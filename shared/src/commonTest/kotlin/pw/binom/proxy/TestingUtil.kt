package pw.binom.proxy

import pw.binom.*
import pw.binom.io.*

suspend fun AsyncOutput.writeBinary(data: ByteArray) {
    data.wrap {
        writeFully(it)
    }
}

suspend fun AsyncOutput.writeText(text: String) {
    writeBinary(text.encodeToByteArray())
}

suspend fun AsyncInput.readBinary(size: Int) = ByteBuffer(DEFAULT_BUFFER_SIZE).use {
    readByteArray(size, it)
}
