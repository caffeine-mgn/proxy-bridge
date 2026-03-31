package pw.binom.utils

import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readAvailable
import io.ktor.utils.io.writeBuffer
import io.ktor.utils.io.writePacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.Source

fun SendChannel<Buffer>.toByteChannel(): ByteWriteChannel = ByteWriteChannelByChannel(this)
fun ReceiveChannel<Buffer>.toByteChannel(): ByteReadChannel = ByteReadChannelByChannel(this)

suspend inline fun SendChannel<Buffer>.send(block: Buffer.() -> Unit) {
    val buffer = Buffer()
    block(buffer)
    send(buffer)
}

@OptIn(InternalAPI::class)
suspend fun connect(
    outcome: SendChannel<Buffer>,
    income: ReceiveChannel<Buffer>,
    a: ByteWriteChannel,
    b: ByteReadChannel,
) {
    coroutineScope {
        listOf(
            launch(Dispatchers.IO) {
                println("ChannelExtensions #1")
                income.consumeEach { buffer ->
                    println("connect:: copy ${buffer.size} bytes from channel to stream")
                    a.writePacket(buffer)
                    a.flush()
                }
            },
            launch(Dispatchers.IO) {
                println("ChannelExtensions #2")
                while (isActive) {
                    val buffer = Buffer()
                    if (b.readBuffer.exhausted()) {
                        b.awaitContent(min = 1)
                    }
                    val wasRead = b.readBuffer.copyTo(buffer)
                    if (wasRead > 0) {
                        println("connect:: copy ${buffer.size} bytes from stream to channel")
                        outcome.send(buffer)
                    }
                }
            }).joinAll()
    }
}

private class ByteWriteChannelByChannel(private val channel: SendChannel<Buffer>) : ByteWriteChannel {
    override val isClosedForWrite: Boolean
        get() = channel.isClosedForSend

    override var closedCause: Throwable? = null
        private set

    private var buffer = Buffer()

    @InternalAPI
    override val writeBuffer: Sink
        get() = buffer

    override suspend fun flush() {
        if (buffer.exhausted()) {
            return
        }
        try {
            channel.send(buffer)
        } catch (e: ClosedSendChannelException) {
            closedCause = e
        }
        buffer = Buffer()
    }

    override suspend fun flushAndClose() {
        flush()
        channel.close()
    }

    override fun cancel(cause: Throwable?) {
        channel.close(cause)
        closedCause = cause
    }

}

private class ByteReadChannelByChannel(private val channel: ReceiveChannel<Buffer>) : ByteReadChannel {
    override var closedCause: Throwable? = null
        private set
    override val isClosedForRead: Boolean
        get() = channel.isClosedForReceive

    private val buffer = Buffer()

    @InternalAPI
    override val readBuffer: Source
        get() = buffer

    override suspend fun awaitContent(min: Int): Boolean {
        while (buffer.size < min) {
            val buffer = try {
                channel.receive()
            } catch (e: ClosedReceiveChannelException) {
                closedCause = e
                return false
            }
            buffer.transferTo(this.buffer)
        }
        return true
    }

    override fun cancel(cause: Throwable?) {
        channel.cancel(CancellationException())
        closedCause = cause
    }
}
