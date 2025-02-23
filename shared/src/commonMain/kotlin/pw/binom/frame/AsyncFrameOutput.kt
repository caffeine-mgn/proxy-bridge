package pw.binom.frame

import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import pw.binom.*
import pw.binom.atomic.AtomicBoolean
import pw.binom.io.*

class AsyncFrameOutput(val output: FrameSender) : AsyncOutput {
    companion object {
        private val protobuf = ProtoBuf
    }

    private val closed = AtomicBoolean(false)
    private val outputBuffer = ByteBuffer(output.bufferSize.asInt)

    suspend fun <T> writeObject(k: KSerializer<T>, value: T) {
        val data = protobuf.encodeToByteArray(k, value)
        writeInt(data.size)
        writeByteArray(data)
    }

    override suspend fun asyncClose() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        try {
            flush()
            output.asyncClose()
        } finally {
            outputBuffer.close()
        }
    }

    override suspend fun flush() {
        if (closed.getValue()) {
            return
        }
        if (outputBuffer.position == 0) {
            return
        }
        outputBuffer.flip()
        while (outputBuffer.hasRemaining) {
            output.sendFrame {
                it.writeFrom(outputBuffer)
            }
        }
        outputBuffer.clear()
    }

    override suspend fun writeInt(value: Int) {
        writeFully(value.toByteArray())
    }

    override suspend fun writeLong(value: Long) {
        writeFully(value.toByteArray())
    }

    suspend fun writeFully(data: ByteArray, length: Int = data.size): Int {
        var writeSize = 0
        fun remaining() = length - writeSize
        while (remaining() > 0) {
            val wrote = write(data, offset = writeSize)
            if (wrote.isNotAvailable) {
                if (writeSize == 0) {
                    throw StreamClosedException()
                } else {
                    throw PackageBreakException("Can't write data. $writeSize bytes was sent")
                }
            }
            writeSize += wrote.length
        }
        return writeSize
    }

    override suspend fun write(data: ByteArray, offset: Int, length: Int): DataTransferSize {
        if (closed.getValue()) {
            return DataTransferSize.CLOSED
        }
        val l = outputBuffer.write(data, offset = offset, length = length)
        if (!outputBuffer.hasRemaining) {
            flush()
        }
        return l
    }

    override suspend fun write(data: ByteBuffer): DataTransferSize {
        if (closed.getValue()) {
            return DataTransferSize.CLOSED
        }
        val l = outputBuffer.write(data)
        if (!outputBuffer.hasRemaining) {
            flush()
        }
        return l
    }
}
