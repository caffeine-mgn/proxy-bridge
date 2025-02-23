package pw.binom

import pw.binom.io.ByteBuffer
import pw.binom.io.DataTransferSize
import pw.binom.io.Input
import kotlin.collections.copyInto

class FixBufferInput(private val size: Int, private val line: Input) {
    private val buffer = ByteArray(8)
    private var bufferFullCursor = 0
    private var bufferReadCursor = 0
    private val dataLenInBuffer
        get() = bufferFullCursor - bufferReadCursor


    fun read(dest: ByteArray, offset: Int = 0, length: Int = dest.size - offset): DataTransferSize {
        if (length <= 0) {
            return DataTransferSize.EMPTY
        }
        var len = length
        var read = 0
        val dataLenInBuffer = dataLenInBuffer
        if (dataLenInBuffer > 0) {
            val lenFromBuffer = minOf(dataLenInBuffer, len)
            buffer.copyInto(
                destination = dest,
                destinationOffset = offset,
                startIndex = bufferReadCursor,
                endIndex = bufferReadCursor + lenFromBuffer
            )
            bufferReadCursor += lenFromBuffer
            len -= lenFromBuffer
            read += lenFromBuffer
        }
        if (len > size) {
            val lenForRead = len - len % size
            val wasRead = line.read(dest = dest, offset = offset + read, length = lenForRead)
            if (wasRead.isNotAvailable){
                if (read>0){
                    return DataTransferSize.ofSize(read)
                } else {
                    return wasRead
                }
            }
            return DataTransferSize.ofSize(wasRead.length + read)
        }
        TODO()
    }
}
