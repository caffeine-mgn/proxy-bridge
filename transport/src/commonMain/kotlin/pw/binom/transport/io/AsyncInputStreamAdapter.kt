package pw.binom.transport.io

import pw.binom.atomic.AtomicBoolean
import pw.binom.coroutines.SimpleAsyncLock
import pw.binom.io.AsyncInput
import pw.binom.io.Available
import pw.binom.io.ByteBuffer
import pw.binom.io.DataTransferSize
import pw.binom.transport.ThreadCoroutineDispatcher
import java.io.InputStream

class AsyncInputStreamAdapter(private val inputStream: InputStream, bufferSize: Int) : AsyncInput {
    init {
        require(bufferSize > 0) { "bufferSize should be more than zero" }
    }

    private val dispatcher = ThreadCoroutineDispatcher("InputStream")
    private val buffer = ByteArray(bufferSize)
    private val closed = AtomicBoolean(false)
    private val writeLock = SimpleAsyncLock()
    override val available: Available
        get() = if (closed.getValue()) Available.Companion.NOT_AVAILABLE else inputStream.available().let {
            when {
                it == 0 -> Available.Companion.UNKNOWN
                it < 0 -> Available.Companion.NOT_AVAILABLE
                else -> Available.Companion.isAvailable(it)
            }
        }

    override suspend fun read(dest: ByteBuffer): DataTransferSize =
        writeLock.synchronize {
            println("AsyncInputStreamAdapter::read reading... buffer.size=${buffer.size}, dest.remaining=${dest.remaining}")
            val l = read(
                dest = buffer,
                offset = 0,
                length = minOf(buffer.size, dest.remaining),
            )
            if (l.isAvailable) {
                dest.write(buffer)
            }
            l
        }

    override suspend fun read(dest: ByteArray, offset: Int, length: Int): DataTransferSize {
        if (closed.getValue()) {
            return DataTransferSize.Companion.CLOSED
        }
        return dispatcher.asyncExecute {
            val l = inputStream.read(dest, offset, length)
            println("AsyncInputStreamAdapter::read was read $l offset=$offset length=$length")
            if (l < 0) {
                DataTransferSize.Companion.CLOSED
            } else {
                DataTransferSize.Companion.ofSize(l)
            }
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
            runCatching { inputStream.close() }
        }
        dispatcher.close()
    }
}
