package pw.binom.frame

import pw.binom.io.*

interface FrameChannel : FrameReceiver, FrameSender

interface FrameDefinition {
    val bufferSize: PackageSize
}

interface FrameReceiver : FrameDefinition, AsyncCloseable {
    suspend fun <T> readFrame(func: (buffer: FrameInput) -> T): FrameResult<T>
}

interface FrameSender : FrameDefinition, AsyncCloseable {
    suspend fun <T> sendFrame(func: (buffer: FrameOutput) -> T): FrameResult<T>
}
