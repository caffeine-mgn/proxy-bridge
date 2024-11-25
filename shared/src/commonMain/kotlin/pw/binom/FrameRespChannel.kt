package pw.binom

import pw.binom.frame.*

class FrameRespChannel(val channel: FrameChannel) : FrameChannel {
    companion object {
        const val RESP: Byte = 0x2
    }

    override suspend fun <T> readFrame(func: (buffer: FrameInput) -> T): FrameResult<T> {
        val r = channel.readFrame(func)
        if (r.isNotClosed) {
            channel.sendFrame { it.writeByte(RESP) }
        }
        return r
    }

    override val bufferSize: PackageSize
        get() = channel.bufferSize - 1

    override suspend fun asyncClose() {
        channel.asyncClose()
    }

    override suspend fun <T> sendFrame(func: (buffer: FrameOutput) -> T): FrameResult<T> {
        val r = channel.sendFrame(func)
        if (r.isNotClosed) {
            channel.readFrame { it.readByte() }
        }
        return r
    }
}
