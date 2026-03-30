package pw.binom

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.nio.ByteBuffer

class VirtualInputStream(val channel: ReceiveChannel<ByteBuffer>) : InputStream() {
    private var currentBuffer: ByteBuffer? = null

    private fun getBuffer(): ByteBuffer {
        while (true) {
            var currentBuffer = currentBuffer
            if (currentBuffer == null) {
                currentBuffer = runBlocking {
                    channel.receive()
                }
            }
            if (!currentBuffer.hasRemaining()) {
                this.currentBuffer = null
                continue
            }
            return currentBuffer
        }
    }

    override fun read(): Int = getBuffer().get().toInt()
}
