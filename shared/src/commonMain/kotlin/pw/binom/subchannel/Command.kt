package pw.binom.subchannel

import pw.binom.frame.FrameChannel

interface Command<T> {
    companion object {
        const val TCP_CONNECT: Byte = 0x1
        const val FS: Byte = 0x2
        const val BENCHMARK: Byte = 0x3
    }

    val cmd: Byte
    suspend fun startClient(channel: FrameChannel)
    suspend fun startServer(channel: FrameChannel): T
}
