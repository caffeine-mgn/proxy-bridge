package pw.binom.frame

import pw.binom.io.*

interface FrameChannel : AsyncCloseable {
    val bufferSize: PackageSize
    suspend fun <T> sendFrame(func: (buffer: FrameOutput) -> T): FrameResult<T>
    suspend fun <T> readFrame(func: (buffer: FrameInput) -> T): FrameResult<T>
}
