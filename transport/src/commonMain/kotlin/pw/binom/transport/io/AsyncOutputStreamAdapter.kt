package pw.binom.transport.io

import pw.binom.atomic.AtomicBoolean
import pw.binom.coroutines.SimpleAsyncLock
import pw.binom.io.AsyncOutput
import pw.binom.io.ByteBuffer
import pw.binom.io.DataTransferSize
import pw.binom.io.PackageBreakException
import pw.binom.io.StreamClosedException
import pw.binom.transport.ThreadCoroutineDispatcher
import java.io.OutputStream

class AsyncOutputStreamAdapter(private val outputStream: OutputStream, bufferSize: Int) : AsyncOutput {
    init {
        require(bufferSize > 0) { "bufferSize should be more than zero" }
    }

    private val dispatcher = ThreadCoroutineDispatcher("InputStream")
    private val buffer = ByteArray(bufferSize)
    private val closed = AtomicBoolean(false)
    private val writeLock = SimpleAsyncLock()

    override suspend fun write(data: ByteBuffer): DataTransferSize {
        if (!data.hasRemaining) {
            return DataTransferSize.Companion.EMPTY
        }
        if (closed.getValue()) {
            return DataTransferSize.Companion.CLOSED
        }
        return writeLock.synchronize {
            val length = data.readInto(buffer)
            try {
                writeFully(buffer, offset = 0, length = length)
                DataTransferSize.Companion.ofSize(length)
            } catch (e: StreamClosedException) {
                DataTransferSize.Companion.CLOSED
            } catch (e: PackageBreakException) {
                DataTransferSize.Companion.CLOSED
            }
        }
    }

    override suspend fun write(data: ByteArray, offset: Int, length: Int): DataTransferSize {
        if (closed.getValue()) {
            return DataTransferSize.Companion.CLOSED
        }
        if (data.isEmpty()) {
            return DataTransferSize.Companion.EMPTY
        }
        dispatcher.asyncExecute {
            outputStream.write(data, offset, length)
            outputStream.flush()
//            println("AsyncOutputStreamAdapter::wrote wrote success $length")
        }
        return DataTransferSize.Companion.ofSize(data.size)
    }

    override suspend fun flush() {
        if (closed.getValue()) {
            return
        }
        dispatcher.asyncExecute {
            outputStream.flush()
        }
    }

    fun free() {
        dispatcher.close()
    }

    override suspend fun asyncClose() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        dispatcher.asyncExecute(true) {
            runCatching { outputStream.close() }
        }
        dispatcher.close()
    }
}
