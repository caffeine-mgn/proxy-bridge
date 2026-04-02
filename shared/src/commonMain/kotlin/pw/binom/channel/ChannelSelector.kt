package pw.binom.channel

import io.klogging.logger
import io.ktor.network.selector.SelectorManager
import pw.binom.multiplexer.DuplexChannel

object ChannelSelector {
    private val logger = logger(this::class)

    suspend fun processing(connection: DuplexChannel, selector: SelectorManager) {
        val buff = connection.receive()
        logger.info { "Income ${buff.size} bytes" }
        val cmd = buff.readByte()
        logger.info { { "Command: $cmd" } }
        when (cmd) {
            TcpConnectChannel.ID -> TcpConnectChannel.income(selector = selector, channel = connection, buffer = buff)
            else -> {
                logger.warn { "Unknown command $cmd" }
                connection.close()
            }
        }
    }
}
