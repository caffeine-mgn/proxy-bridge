package pw.binom

import kotlinx.coroutines.isActive
import pw.binom.atomic.AtomicBoolean
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.*
import kotlin.coroutines.coroutineContext

class ClosableAsyncChannel(
    val stream: AsyncChannel,
    val closeStream: suspend (AsyncChannel) -> Unit = { it.asyncClose() },
) : AsyncChannel {
    companion object {
        const val DATA: Byte = 1
        const val CLOSED: Byte = 2
    }

    override val available: Available
        get() = Available.UNKNOWN

    private val writeBuffer = byteBuffer(8)
    private val readBuffer = byteBuffer(8)

    private var packageSize = 0
    private var closed = AtomicBoolean(false)
    private var closeReceived = AtomicBoolean(false)
    private val lock = SpinLock()

    val isClosed
        get() = closed.getValue()

    private suspend fun skipUntilClose() {
        if (!closeReceived.compareAndSet(false, true)) {
            return
        }
        try {
            if (packageSize > 0) {
                stream.skip(bytes = packageSize.toLong(), buffer = readBuffer)
            }
            while (coroutineContext.isActive) {
                val byte = stream.readByte(readBuffer)
                when (byte) {
                    DATA -> {
                        val size = stream.readInt(readBuffer)
                        stream.skip(bytes = size.toLong(), buffer = readBuffer)
                    }

                    CLOSED -> {
                        break
                    }
                }
            }
        } catch (e: StreamClosedException) {
            makeClose()
        } catch (e: EOFException) {
            makeClose()
        } catch (_: Throwable) {
            // Ignore
        }
    }

    override suspend fun read(dest: ByteBuffer): DataTransferSize {
        if (closed.getValue()) {
            return DataTransferSize.CLOSED
        }
        while (coroutineContext.isActive) {
            lock.lock()
            if (packageSize > 0) {
                if (dest.remaining > packageSize) {
                    dest.limit = dest.position + packageSize
                }
                lock.unlock()
                val len = stream.read(dest)
                if (len.isAvailable) {
                    lock.synchronize {
                        packageSize -= len.length
                    }
                }
                return len
            } else {
                lock.unlock()
            }
            val cmd = try {
                stream.readByte(readBuffer)
            } catch (e: StreamClosedException) {
                closeReceived.setValue(true)
                closed.setValue(true)
                makeClose()
                return DataTransferSize.CLOSED
            } catch (e: EOFException) {

                closeReceived.setValue(true)
                closed.setValue(true)
                makeClose()
                return DataTransferSize.CLOSED
            }
            when (cmd) {
                DATA -> {
                    val size = try {
                        stream.readInt(readBuffer)
                    } catch (e: EOFException) {
                        closeReceived.setValue(true)
                        closed.setValue(true)
                        makeClose()
                        return DataTransferSize.CLOSED
                    }
                    lock.synchronize {
                        packageSize = size
                    }
                }

                CLOSED -> {
                    closeReceived.setValue(true)
                    sendClose()
                    return DataTransferSize.CLOSED
                }

                else -> TODO("Unknown cmd $cmd")
            }
        }
        sendClose()
        return DataTransferSize.CLOSED
    }

    override suspend fun write(data: ByteBuffer): DataTransferSize {
        if (!data.hasRemaining) {
            return DataTransferSize.EMPTY
        }
        val l = DataTransferSize.ofSize(data.remaining)
        writeBuffer.clear()
        writeBuffer.put(DATA)
        writeBuffer.writeInt(data.remaining)
        writeBuffer.flip()
        stream.writeFully(writeBuffer)
//        stream.writeByte(DATA, writeBuffer)
//        stream.writeInt(data.remaining, writeBuffer)
        stream.writeFully(data)
        return l
    }

    override suspend fun asyncClose() {
        runCatching {
            sendClose()
        }
    }

    override suspend fun flush() {
        if (!closed.getValue()) {
            stream.flush()
        }
    }

    private suspend fun makeClose() {
        readBuffer.close()
        writeBuffer.close()
        closeStream(stream)
    }

    private suspend fun sendClose() {
        if (closed.compareAndSet(expected = false, new = true)) {
            try {
                stream.writeByte(CLOSED, writeBuffer)
                stream.flush()
                skipUntilClose()
            } finally {
                makeClose()
            }
        }
    }
}
