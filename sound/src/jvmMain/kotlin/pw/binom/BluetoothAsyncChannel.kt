package pw.binom

import pw.binom.atomic.AtomicBoolean
import pw.binom.io.*
import pw.binom.logger.Logger
import javax.microedition.io.StreamConnection

class BluetoothAsyncChannel(
    private val connection: StreamConnection
) : AsyncChannel {
    private val input = connection.openInputStream()
    private val output = connection.openOutputStream()
    private val inDispatcher = ThreadCoroutineDispatcher(name = "inDispatcher")
    private val outDispatcher = ThreadCoroutineDispatcher(name = "outDispatcher")
    private val logger by Logger.ofThisOrGlobal

    override val available: Available
        get() = Available.of(input.available())
    private val closed = AtomicBoolean(false)

    private fun closeIO() {
        try {
            input.close()
            output.close()
        } catch (e: Throwable) {
            // ignore
        } finally {
            connection.close()
        }
    }

    override suspend fun asyncClose() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        inDispatcher.close()
        outDispatcher.close()
        closeIO()
    }

    override suspend fun flush() {
        closed.getValue()
        outDispatcher.asyncExecute {
            output.flush()
        }
    }

    override suspend fun read(dest: ByteBuffer): DataTransferSize {
        if (closed.getValue()) {
            return DataTransferSize.CLOSED
        }
        return inDispatcher.asyncExecute {
            val b = ByteArray(dest.remaining)
            val len = try {
                input.read(b)
            } catch (e: IOException) {
                runCatching { closeIO() }
                -1
            }
            when {
                len > 0 -> {
                    dest.write(b, 0, len)
                    DataTransferSize.ofSize(len)
                }

                len == 0 -> DataTransferSize.EMPTY
                else -> DataTransferSize.CLOSED
            }
        }
    }

    override suspend fun read(dest: ByteArray, offset: Int, length: Int): DataTransferSize {
        if (closed.getValue()) {
            return DataTransferSize.CLOSED
        }
        return inDispatcher.asyncExecute {
            val len = try {
                input.read(dest, offset, length)
            } catch (e: IOException) {
                runCatching { closeIO() }
                -1
            }
            if (len == -1) {
                DataTransferSize.CLOSED
            } else {
                DataTransferSize.ofSize(len)
            }
        }
    }

    override suspend fun write(data: ByteBuffer): DataTransferSize {
        if (closed.getValue()) {
            return DataTransferSize.CLOSED
        }
        if (!data.hasRemaining) {
            return DataTransferSize.EMPTY
        }
        return outDispatcher.asyncExecute {
            try {
                val bytes = data.toByteArray()
                output.write(bytes)
                data.position += bytes.size
                DataTransferSize.ofSize(bytes.size)
            } catch (e: Throwable) {
                e.printStackTrace()
                throw e
            }
        }
    }

    override suspend fun write(data: ByteArray, offset: Int, length: Int): DataTransferSize {
        if (closed.getValue()) {
            return DataTransferSize.CLOSED
        }
        return outDispatcher.asyncExecute {
            try {
                output.write(data, offset, length)
                DataTransferSize.ofSize(length)
            } catch (e: IOException) {
                runCatching { closeIO() }
                DataTransferSize.CLOSED
            }
        }
    }
}
