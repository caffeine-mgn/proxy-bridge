package pw.binom

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import pw.binom.atomic.AtomicBoolean
import pw.binom.io.AsyncChannel
import pw.binom.io.Available
import pw.binom.io.ByteBuffer
import pw.binom.io.ClosedException
import pw.binom.io.DataTransferSize

class VirtualChannel : AsyncChannel {
    companion object {
        fun create(func: suspend AsyncChannel.() -> Unit): VirtualChannel {
            val channel = VirtualChannel()
            GlobalScope.launch {
                channel.internal(func)
            }
            return channel
        }
    }

    private val internalInput = TestingChannel()
    private val internalOutput = TestingChannel()

    private val internal = object : AsyncChannel {
        override val available
            get() = internalInput.available

        override suspend fun asyncClose() {
            TODO("Not yet implemented")
        }

        override suspend fun flush() {
            internalOutput.flush()
        }

        override suspend fun read(dest: ByteBuffer): DataTransferSize = internalInput.read(dest)

        override suspend fun write(data: ByteBuffer): DataTransferSize = internalOutput.write(data)
    }

    suspend fun <T> internal(func: suspend AsyncChannel.() -> T): T =
        if (isClosed) {
            throw ClosedException()
        } else {
            try {
                func(internal)
            } finally {
                asyncClose()
            }
        }

    override val available
        get() = internalOutput.available

    private val closed = AtomicBoolean(false)

    val isClosed
        get() = closed.getValue()

    override suspend fun asyncClose() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        internalInput.asyncCloseAnyway()
        internalOutput.asyncCloseAnyway()
    }

    override suspend fun flush() {
        internalInput.flush()
    }

    override suspend fun read(dest: ByteBuffer): DataTransferSize = internalOutput.read(dest)

    override suspend fun write(data: ByteBuffer): DataTransferSize = internalInput.write(data)
}
