package pw.binom.bluetooth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.EOFException
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import pw.binom.io.BluetoothConnection
import pw.binom.multiplexer.lebULong
import pw.binom.multiplexer.readFully
import javax.microedition.io.StreamConnection

fun BluetoothConnection.Companion.create(connection: StreamConnection): BluetoothConnection {
    val outputStream = connection.openOutputStream().asSink().buffered()
    val inputStream = connection.openInputStream().asSource().buffered()

    val output = Channel<Buffer>(Channel.UNLIMITED)
    val input = Channel<Buffer>(Channel.UNLIMITED)

    val writeJob = CoroutineScope(Dispatchers.IO).launch {
        output.consumeEach { buffer ->
            outputStream.lebULong(buffer.size.toULong())
            outputStream.write(buffer, buffer.size)
            outputStream.flush()
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
            }
        }
    }
    return BluetoothConnection(
        income = input,
        outcome = output,
        connect = {
            runCatching { writeJob.cancel() }
            runCatching { readJob.cancel() }
            runCatching { outputStream.close() }
            runCatching { inputStream.close() }
            runCatching { connection.close() }
        })
}
