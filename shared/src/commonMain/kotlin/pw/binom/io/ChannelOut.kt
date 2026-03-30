package pw.binom.io

import kotlinx.io.Buffer

interface ChannelOut : AutoCloseable {
    suspend fun write(source: Buffer, byteCount: Long)
    suspend fun writeByte(value: Byte)
    suspend fun writeShort(value: Short)
    suspend fun flush()
}
