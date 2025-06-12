package pw.binom
/*
import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.frame.PackageSize
import pw.binom.io.ByteBuffer
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import java.nio.ByteBuffer as JByteBuffer

@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
class ComConnection(
    private val income: SendChannel<ByteBuffer>,
    private val outcome: ReceiveChannel<ByteBuffer>,
    val port: SerialPort
) : Closeable {
    private val closed = AtomicBoolean(false)

    init {
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
    }

    override fun close() {
        readThread.interrupt()
        writeThread.interrupt()
    }

    private var water: CancellableContinuation<Unit>? = null

    private fun closeAndResume() {
        val wasClosed = closed.get()
        close()
        if (!wasClosed) {
            water?.resume(Unit)
        }
    }

    val readThread = Thread {
        try {
            while (!closed.get()) {
                val buff = ByteArray(PackageSize.SIZE_BYTES)
                port.fullingRead(buff)
                val size = PackageSize(buff)
                val buffer = JByteBuffer.allocate(size.asInt)
                checkNotNull(buffer.hb) { "ByteBuffer data is null" }
                port.fullingRead(buffer.hb)
                runBlocking {
                    income.send(ByteBuffer(buffer))
                }
            }
        } finally {
            closeAndResume()
        }
    }

    val writeThread = Thread {
        try {
            while (!closed.get()) {
                val data = runBlocking { outcome.receive() }
                val sizeData = PackageSize(data.remaining).toByteArray()
                val dataForSend = ByteArray(data.remaining)
                data.native.get(dataForSend)

                port.fullingWrite(sizeData)
                port.fullingWrite(dataForSend)
            }
        } finally {
            closeAndResume()
        }
    }

    suspend fun processing() {
        suspendCancellableCoroutine {
            water = it
            it.invokeOnCancellation {
                close()
            }
            readThread.start()
            writeThread.start()
        }
    }
}

private fun SerialPort.fullingWrite(buffer: ByteArray) {
    var wrote = 0
    while (wrote < buffer.size) {
        val len = writeBytes(buffer, buffer.size - wrote, wrote)
        when {
            len > 0 -> wrote += len
            len == 0 -> throw IOException("COM port wrote 0 bytes")
            else -> throw IOException("COM port wrote invalid bytes count: $len")
        }
    }
}

private fun SerialPort.fullingRead(buffer: ByteArray) {
    var read = 0
    while (read < buffer.size) {
        val len = readBytes(buffer, buffer.size - read, read)
        when {
            len > 0 -> read += len
            len == 0 -> throw IOException("COM port returns 0 bytes")
            else -> throw IOException("COM port returns invalid bytes count: $len")
        }
    }
}
*/
