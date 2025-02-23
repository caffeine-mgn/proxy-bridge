package pw.binom.sound

import pw.binom.frame.*

class SoundFrameChannel(override val bufferSize: PackageSize) :FrameChannel {
    override suspend fun <T> readFrame(func: (buffer: FrameInput) -> T): FrameResult<T> {
        TODO("Not yet implemented")
    }

    override suspend fun asyncClose() {
        TODO("Not yet implemented")
    }

    override suspend fun <T> sendFrame(func: (buffer: FrameOutput) -> T): FrameResult<T> {
        TODO("Not yet implemented")
    }
}
