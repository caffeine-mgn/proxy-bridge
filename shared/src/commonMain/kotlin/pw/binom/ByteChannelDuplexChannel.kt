package pw.binom

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.asSink
import io.ktor.utils.io.asSource
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
import pw.binom.com.SerialConnection
import pw.binom.multiplexer.DuplexChannel
import pw.binom.multiplexer.lebULong
import pw.binom.multiplexer.readFully

class ByteChannelDuplexChannel(
    private val connect: AutoCloseable,
    override val income: ReceiveChannel<Buffer>,
    override val outcome: SendChannel<Buffer>
) : DuplexChannel {

    companion object {
        fun create(
            rr: ByteReadChannel,
            ww: ByteWriteChannel,
            onClosed: () -> Unit = {},
        ): SerialConnection {
            val output = Channel<Buffer>(Channel.UNLIMITED)
            val input = Channel<Buffer>(Channel.UNLIMITED)
            val outputStream = ww.asSink().buffered()
            val inputStream = rr.asSource().buffered()
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
