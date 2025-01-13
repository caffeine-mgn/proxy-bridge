package pw.binom.frame

import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import pw.binom.*
import pw.binom.atomic.AtomicBoolean
import pw.binom.io.*

class AsyncFrameInput(val input: FrameReceiver) : AsyncInput {

    companion object{
        private val protobuf = ProtoBuf
    }

    private val closed = AtomicBoolean(false)
    private val inputBuffer = ByteBuffer(input.bufferSize.asInt).empty()

    override val available: Int
        get() = when {
            closed.getValue() -> 0
            inputBuffer.hasRemaining -> inputBuffer.remaining
            else -> -1
        }

    override suspend fun asyncClose() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        try {
            input.asyncClose()
        } finally {
            inputBuffer.close()
        }
    }

    suspend fun <T> readObject(k: KSerializer<T>):T {
        val size = readInt()
        val data = readByteArray(size)
        return protobuf.decodeFromByteArray(k, data)
    }


    suspend fun readInt(): Int {
        val buf = ByteArray(Int.SIZE_BYTES)
        readFully(buf)
        return Int.fromBytes(buf)
    }

    suspend fun readLong(): Long {
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

    override suspend fun read(dest: ByteArray, offset: Int, length: Int): DataTransferSize {
        if (closed.getValue()) {
            return DataTransferSize.CLOSED
        }
        if (!inputBuffer.hasRemaining) {
            while (true) {
                inputBuffer.clear()
                val e = input.readFrame { it.readInto(inputBuffer) }.valueOrNull ?: return DataTransferSize.CLOSED
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
                val e = input.readFrame { it.readInto(inputBuffer) }.valueOrNull ?: return DataTransferSize.CLOSED
                if (e > 0) {
                    inputBuffer.flip()
                    break
                }
            }
        }
        return DataTransferSize.ofSize(inputBuffer.readInto(dest))
    }
}
