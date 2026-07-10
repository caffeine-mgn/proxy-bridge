package pw.binom.proxy.channel

import kotlinx.io.Buffer
import pw.binom.multiplexer.DuplexChannel

interface ChannelHandler {
    val id: Byte
    suspend fun income(channel: DuplexChannel, buffer: Buffer)
}
