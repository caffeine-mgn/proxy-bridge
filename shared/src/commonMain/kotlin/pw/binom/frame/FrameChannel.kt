package pw.binom.frame

import pw.binom.io.*

interface FrameChannel : FrameReceiver, FrameSender {
    companion object;
}

interface FrameDefinition {
    val bufferSize: PackageSize
}

interface FrameChannelWithMeta : FrameChannel {
    companion object;
    val meta: MutableMap<String, String>
}

interface FrameReceiverWithMeta : FrameReceiver {
    val meta: MutableMap<String, String>
}

interface FrameReceiver : FrameDefinition, AsyncCloseable {
    suspend fun <T> readFrame(func: (buffer: FrameInput) -> T): FrameResult<T>
}

interface FrameSender : FrameDefinition, AsyncCloseable {
    suspend fun <T> sendFrame(func: (buffer: FrameOutput) -> T): FrameResult<T>
}
