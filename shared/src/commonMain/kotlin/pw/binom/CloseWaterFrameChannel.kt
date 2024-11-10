package pw.binom

import pw.binom.frame.FrameChannel
import pw.binom.frame.FrameInput
import pw.binom.frame.FrameOutput
import pw.binom.frame.FrameResult
import pw.binom.frame.PackageSize

class CloseWaterFrameChannel<T : FrameChannel>(val other: T, val closeListener: suspend (T) -> Unit) : FrameChannel {
    override val bufferSize: PackageSize
        get() = other.bufferSize

    override suspend fun <T> sendFrame(func: (FrameOutput) -> T): FrameResult<T> =
        other.sendFrame(func)

    override suspend fun <T> readFrame(func: (FrameInput) -> T): FrameResult<T> =
        other.readFrame(func)

    override suspend fun asyncClose() {
        closeListener(other)
    }
}
