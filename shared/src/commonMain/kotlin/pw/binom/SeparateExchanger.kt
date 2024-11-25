package pw.binom

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import pw.binom.frame.FrameChannel
import pw.binom.io.ByteBuffer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Копирует данные из канала [from] в [destination]
 */
suspend fun FrameChannel.copyTo(
    destination: FrameChannel,
    readContext: CoroutineContext? = null,
    writeContext: CoroutineContext? = null,
) {
    val from = this
    require(from.bufferSize == destination.bufferSize) { "Buffer size of $from and $destination should be equals" }
    val buffer = byteBuffer(from.bufferSize.asInt)
    val channel = Channel<Unit>(onUndeliveredElement = {})
    val syncPoint = AsyncSyncPoint()

    val readContext = readContext ?: coroutineContext
    val writeContext = writeContext ?: coroutineContext
    try {
        val readJob = GlobalScope.launch(readContext) {
            readProcessing(source = from, buffer = buffer, channel = channel, syncPoint = syncPoint)
        }
        val writeJob = GlobalScope.launch(writeContext) {
            writeProcessing(destination = destination, buffer = buffer, channel = channel, syncPoint = syncPoint)
        }
        joinAll(readJob, writeJob)
    } finally {
        buffer.close()
        channel.close()
    }
}

private suspend fun readProcessing(
    source: FrameChannel,
    buffer: ByteBuffer,
    channel: Channel<Unit>,
    syncPoint: AsyncSyncPoint,
) {
    try {
        while (coroutineContext.isActive) {
            val result = source.readFrame { buf ->
                buffer.clear()
                buf.readInto(buffer)
                buffer.flip()
            }
            if (result.isClosed) {
                break
            }
            channel.send(Unit)
            syncPoint.lock()
        }
    } finally {
        channel.close()
    }
}

private suspend fun writeProcessing(
    destination: FrameChannel,
    buffer: ByteBuffer, channel: Channel<Unit>, syncPoint: AsyncSyncPoint
) {
    while (coroutineContext.isActive) {
        try {
            channel.receive()
        } catch (_: CancellationException) {
            syncPoint.release()
            break
        } catch (_: ClosedReceiveChannelException) {
            break
        }
        val result = destination.sendFrame { buf ->
            buf.writeFrom(buffer)
        }
        if (result.isClosed) {
            channel.close()
            syncPoint.release()
            return
        }
        syncPoint.release()
    }
}
