package pw.binom.subchannel

import pw.binom.frame.FrameChannel
import pw.binom.frame.FrameChannelWithMeta

interface Command<T> {
    companion object {
        const val TCP_CONNECT: Byte = 0x1
        const val FS: Byte = 0x2
        const val BENCHMARK: Byte = 0x3
    }

    val cmd: Byte
    suspend fun startClient(channel: FrameChannelWithMeta)
    suspend fun startServer(channel: FrameChannelWithMeta): T
}
