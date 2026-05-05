package pw.binom.dbus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.EOFException
import kotlinx.io.IOException
import kotlinx.io.buffered
import pw.binom.asSink
import pw.binom.asSource
import pw.binom.io.BluetoothConnection
import pw.binom.multiplexer.lebULong
import pw.binom.multiplexer.readFully
import java.io.RandomAccessFile
import kotlin.time.Duration.Companion.seconds

fun Rfcomm.asBluetoothConnection(onClose:()->Unit = {}): BluetoothConnection {
    val randomAccess = RandomAccessFile(file, "rw")
    val outputStream = randomAccess.asSink().buffered()
    val inputStream = randomAccess.asSource().buffered()

    val output = Channel<Buffer>(Channel.UNLIMITED)
    val input = Channel<Buffer>(Channel.UNLIMITED)

    val writeJob = CoroutineScope(Dispatchers.IO).launch {
        try {
            output.consumeEach { buffer ->
                outputStream.lebULong(buffer.size.toULong())
                outputStream.write(buffer, buffer.size)
                outputStream.flush()
            }
        } catch (e: IOException){
            output.close(e)
        }
    }
    val readJob = CoroutineScope(Dispatchers.IO).launch {
        while (isActive) {
            try {
                val size = inputStream.lebULong()
                val buffer = Buffer()
                inputStream.readFully(buffer, size.toLong())
                input.send(buffer)
            } catch (_: EOFException) {
                input.close()
                break
            } catch (_: ClosedReceiveChannelException) {
                break
            }
        }
    }
    val disconnectedJob = CoroutineScope(Dispatchers.IO).launch {
        device.awaitUntilConnected()
        while (isActive) {
            if (!isConnected()) {
                break
            }
            delay(5.seconds)
        }
        runCatching { outputStream.close() }
        runCatching { inputStream.close() }
        runCatching { writeJob.cancel() }
        runCatching { readJob.cancel() }
        runCatching { close() }
    }
    return BluetoothConnection(
        income = input, outcome = output, connect = {
            runCatching { writeJob.cancel() }
            runCatching { readJob.cancel() }
            runCatching { disconnectedJob.cancel() }
            runCatching { close() }
            runCatching { onClose() }
        })
}
