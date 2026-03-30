package pw.binom.io

import kotlinx.io.Buffer
import java.io.OutputStream

interface ChannelIn : AutoCloseable {
    suspend fun readTo(sink: Buffer, byteCount: Long): Long
    suspend fun readTo(out: OutputStream, byteCount: Long): Long
    suspend fun readTo(out: AsyncOutput, byteCount: Long): DataTransferSize
    suspend fun readByte(): Byte
    suspend fun readShort(): Short
}
