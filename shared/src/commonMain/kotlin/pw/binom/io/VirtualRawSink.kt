package pw.binom.io

import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.RawSink

class VirtualRawSink(
    private val virtualOutput: VirtualOutput,
) : RawSink {
    override fun write(source: Buffer, byteCount: Long) {
        runBlocking {
            virtualOutput.write(source, byteCount)
        }
    }

    override fun flush() {
        runBlocking {
            virtualOutput.flush()
        }
    }

    override fun close() {
        virtualOutput.close()
    }
}
