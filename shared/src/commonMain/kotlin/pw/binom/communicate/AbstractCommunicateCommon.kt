package pw.binom.communicate

import pw.binom.io.AsyncInput
import pw.binom.io.ByteBuffer
import pw.binom.io.DataTransferSize

@Deprecated(message = "Not use it")
abstract class AbstractCommunicateCommon : CommunicateCommon {
    private var currentBuffer: ByteBuffer? = null
    private val input = object : AsyncInput {
        override val available: Int
            get() = -1

        override suspend fun asyncClose() {
            TODO("Not yet implemented")
        }

        override suspend fun read(dest: ByteBuffer): DataTransferSize {

            TODO("Not yet implemented")
        }

    }

    final override fun incomeFrame(data: ByteBuffer) {
        currentBuffer = data
        income(input)
        if (data.hasRemaining) {
            TODO("Not all data was read")
        }
    }

    protected abstract fun income(data: AsyncInput)
}
