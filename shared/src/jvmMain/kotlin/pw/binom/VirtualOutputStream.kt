package pw.binom

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking
import pw.binom.frame.PackageSize
import java.io.OutputStream
import java.nio.ByteBuffer

class VirtualOutputStream(
    private val channel: SendChannel<ByteBuffer>,
    private var maxBufferSize: PackageSize,
) : OutputStream() {

    private var buffer: ByteBuffer = ByteBuffer.allocate(maxBufferSize.asInt)

    override fun write(b: Int) {
        if (!buffer.hasRemaining()) {
            flush()
        }
        buffer.put(b.toByte())
    }

    override fun flush() {
        if (buffer.position() == 0) {
            return
        }
        var b = buffer
        buffer = ByteBuffer.allocate(maxBufferSize.asInt)
        runBlocking {
            channel.send(b)
        }
    }
}
