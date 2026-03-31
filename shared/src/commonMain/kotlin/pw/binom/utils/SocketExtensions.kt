package pw.binom.utils

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import pw.binom.multiplexer.DuplexChannel

@OptIn(InternalAPI::class)
suspend fun Socket.connect(channel: DuplexChannel) {
    listOf(coroutineScope {
        launch {
            val write = openWriteChannel()
            channel.income.consumeEach { buffer ->
                write.writeBuffer.write(buffer, buffer.size)
                write.flush()
            }
        }
    }, coroutineScope {
        launch {
            val read = openReadChannel()
            while (isActive) {
                val buffer = Buffer()
                while (true) {
                    val wasRead = read.readBuffer.readAvailable(buffer)
                    if (wasRead > 0) {
                        channel.outcome.send(buffer)
                        break
                    }
                    read.awaitContent(min = 1)
                }
            }
        }
    }).joinAll()
}
