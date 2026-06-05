package pw.binom.channel

import io.ktor.network.selector.SelectorManager
import kotlinx.io.Buffer
import pw.binom.multiplexer.DuplexChannel

interface ChannelHandler {
    val id: Byte
    suspend fun income(channel: DuplexChannel, buffer: Buffer)
}
