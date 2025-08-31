package pw.binom.transport.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import pw.binom.io.ByteBuffer
import pw.binom.io.use
import pw.binom.transport.MultiplexSocket
import pw.binom.transport.Service
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class EchoService(
    val scope: CoroutineScope = GlobalScope,
    val context: CoroutineContext = EmptyCoroutineContext,
) : Service {
    companion object {
        const val ID = 9
    }

    override fun income(socket: MultiplexSocket) {
        scope.launch(context) {
            socket.use { socket ->
                val size = socket.input.readInt()
                ByteBuffer(size).use { buffer ->
                    socket.input.readFully(buffer)
                    buffer.flip()
                    socket.output.writeFully(buffer)
                }
            }
        }
    }
}
