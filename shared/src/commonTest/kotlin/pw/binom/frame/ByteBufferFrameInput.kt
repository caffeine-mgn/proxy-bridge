package pw.binom.frame

import pw.binom.io.ByteArrayOutput
import pw.binom.io.ByteBuffer
import pw.binom.io.holdState
import pw.binom.wrap

class ByteBufferFrameInput(public override val buffer: ByteBuffer) : AbstractByteBufferFrameInput() {

    companion object {
        operator fun invoke(func: ByteArrayOutput.() -> Unit): ByteBufferFrameInput {
            val o = ByteArrayOutput()
            func(o)
            return ByteBufferFrameInput(ByteBuffer.wrap(o.toByteArray()))
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString(): String {
        val str = buffer.holdState {
            it.toByteArray().toHexString()
        }
        return "ByteBufferFrameInput($str)"
    }
}
