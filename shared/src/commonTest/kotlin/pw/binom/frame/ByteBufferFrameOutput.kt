package pw.binom.frame

import pw.binom.io.ByteBuffer
import pw.binom.io.holdState

class ByteBufferFrameOutput(public override val buffer: ByteBuffer) : AbstractByteBufferFrameOutput() {
    @OptIn(ExperimentalStdlibApi::class)
    override fun toString(): String {
        val str = buffer.holdState {
            it.flip()
            it.toByteArray().toHexString()
        }
        return "ByteBufferFrameOutput($str)"
    }
}
