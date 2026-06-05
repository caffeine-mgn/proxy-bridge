package pw.binom.com

import com.fazecast.jSerialComm.SerialPort
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.EOFException
import kotlinx.io.buffered
import pw.binom.multiplexer.DuplexChannel
import pw.binom.multiplexer.lebULong
import pw.binom.multiplexer.readFully

class SerialConnection(
    private val connect: AutoCloseable,
    override val income: ReceiveChannel<Buffer>,
    override val outcome: SendChannel<Buffer>
) : DuplexChannel {

    companion object {
        fun create(
            port: SerialPort,
            onClosed: () -> Unit = {},
        ): SerialConnection {
            if (!port.isOpen) {
                if (!port.openPort()) {
                    throw IllegalStateException("Can not open ${port.systemPortName}")
                }
            }

            val output = Channel<Buffer>(Channel.UNLIMITED)
            val input = Channel<Buffer>(Channel.UNLIMITED)

            val outputStream = RawSinkSerialPort(port).buffered()
            val inputStream = RawSourceSerialPort(port).buffered()

            val writeJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    output.consumeEach { buffer ->
                        outputStream.lebULong(buffer.size.toULong())
                        outputStream.write(buffer, buffer.size)
                        runCatching { outputStream.flush() }
                    }
                } catch (e: CancellationException) {
                    // do nothing
                } catch (e: Throwable) {
                    println("SerialConnection::writing error: ${e.stackTraceToString()}")
                }
            }
            val readJob = CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    try {
                        val size = inputStream.lebULong()
                        val buffer = Buffer()
                        inputStream.readFully(buffer, size.toLong())
                        input.send(buffer)
                    } catch (e: CancellationException) {
                        // do nothing
                    } catch (_: EOFException) {
                        input.close()
                        break
                    } catch (e: Throwable) {
                        println("SerialConnection::reading error: ${e.stackTraceToString()}")
                    }
                }
            }
            return SerialConnection(
                income = input,
                outcome = output,
                connect = {
                    println("Closing COM connection...")
                    runCatching { writeJob.cancel() }
                    runCatching {
//                        println("Try to cancel ${Throwable().stackTraceToString()}")
                        readJob.cancel()
                    }
                    runCatching { outputStream.close() }
                    runCatching { inputStream.close() }
                    runCatching { port.closePort() }
                    runCatching { onClosed() }
                })
        }
    }

    var isClosed = false
        private set

    override fun close() {
        try {
            connect.close()
        } finally {
            isClosed = true
        }
    }
}
