package pw.binom.frame

import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import pw.binom.*
import pw.binom.atomic.AtomicBoolean
import pw.binom.frame.AsyncFrameOutput.Companion
import pw.binom.io.*

class FrameAsyncChannel(private val channel: FrameChannel) : AsyncChannel {

    companion object {
        private val protobuf = ProtoBuf
    }

    private val closed = AtomicBoolean(false)
    private val inputBuffer = ByteBuffer(channel.bufferSize.asInt).empty()
    private val outputBuffer = ByteBuffer(channel.bufferSize.asInt)

    override val available: Available
        get() = when {
            closed.getValue() -> Available.NOT_AVAILABLE
            inputBuffer.hasRemaining -> Available.of(inputBuffer.remaining)
            else -> Available.UNKNOWN
        }

    override suspend fun asyncClose() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        try {
            flush()
            channel.asyncClose()
        } finally {
            inputBuffer.close()
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
            channel.sendFrame {
                it.writeFrom(outputBuffer)
            }
        }
        outputBuffer.clear()
    }

    override suspend fun read(dest: ByteArray, offset: Int, length: Int): DataTransferSize {
        if (closed.getValue()) {
            return DataTransferSize.CLOSED
        }
        if (!inputBuffer.hasRemaining) {
            while (true) {
                inputBuffer.clear()
                val e = channel.readFrame { it.readInto(inputBuffer) }.valueOrNull ?: return DataTransferSize.CLOSED
                if (e > 0) {
                    inputBuffer.flip()
                    break
                }
            }
        }
        return DataTransferSize.ofSize(inputBuffer.readInto(dest = dest, offset = offset, length = length))
    }

    override suspend fun read(dest: ByteBuffer): DataTransferSize {
        if (closed.getValue()) {
            return DataTransferSize.CLOSED
        }
        if (!inputBuffer.hasRemaining) {
            while (true) {
                inputBuffer.clear()
                val e = channel.readFrame { it.readInto(inputBuffer) }.valueOrNull ?: return DataTransferSize.CLOSED
                if (e > 0) {
                    inputBuffer.flip()
                    break
                }
            }
        }
        return DataTransferSize.ofSize(inputBuffer.readInto(dest))
    }

    suspend fun <T> readObject(k: KSerializer<T>): T {
        val size = readInt()
        val data = readByteArray(size)
        return protobuf.decodeFromByteArray(k, data)
    }

    override suspend fun readBoolean() = readByte() > 0
    suspend fun writeBoolean(bool: Boolean) {
        writeByte(if (bool) 100 else 0)
    }

//    suspend fun readByte(): Byte {
//        val r = ByteArray(1)
//        readFully(r)
//        return r[0]
//    }
//
//    suspend fun writeByte(value: Byte) {
//        writeFully(ByteArray(1) { value })
//    }


    override suspend fun readInt(): Int {
        val buf = ByteArray(Int.SIZE_BYTES)
        readFully(buf)
        return Int.fromBytes(buf)
    }

    override suspend fun readLong(): Long {
        val buf = ByteArray(Long.SIZE_BYTES)
        readFully(buf)
        return Long.fromBytes(buf)
    }

    suspend fun readFully(dest: ByteArray, length: Int = dest.size): Int {
        var wasRead = 0
        fun remaining() = length - wasRead
        while (remaining() > 0) {
            val read = read(dest, offset = wasRead)
            if (read.isNotAvailable && remaining() > 0) {
                val msg = "Full message $length bytes, can't read ${remaining()} bytes"
                if (wasRead > 0) {
                    throw PackageBreakException("$msg. Was read $wasRead bytes")
                } else {
                    throw EOFException(msg)
                }
            }
            wasRead += read.length
        }
        return length
    }

    override suspend fun writeInt(value: Int) {
        writeFully(value.toByteArray())
    }

    override suspend fun writeLong(value: Long) {
        writeFully(value.toByteArray())
    }

    suspend fun <T> writeObject(k: KSerializer<T>, value: T) {
        val data = protobuf.encodeToByteArray(k, value)
        writeInt(data.size)
        writeByteArray(data)
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
