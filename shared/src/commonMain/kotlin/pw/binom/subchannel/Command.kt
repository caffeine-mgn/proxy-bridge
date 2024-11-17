package pw.binom.subchannel

import pw.binom.frame.FrameChannel

interface Command<T> {
    companion object {
        const val TCP_CONNECT: Byte = 0x1
    }

    val cmd: Byte
    suspend fun startClient(channel: FrameChannel)
    suspend fun startServer(channel: FrameChannel): T
}
