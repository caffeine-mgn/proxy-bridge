package pw.binom.proxy.services

import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.dsl.module
import pw.binom.proxy.channel.ChannelHandler
import pw.binom.multiplexer.DuplexChannel

class ChannelSelectorService : KoinComponent {
    companion object {
        val module = module {
            single { ChannelSelectorService() }
        }
    }

    private val logger = KotlinLogging.logger { }
    private val handlers by lazy { getKoin().getAll<ChannelHandler>().associateBy { it.id } }

    suspend fun processing(connection: DuplexChannel) {
        try {
            val buff = connection.receive()
            val cmd = buff.readByte()

            val handler = handlers[cmd]
            if (handler == null) {
                logger.warn { "ChannelSelectorService: unknown cmd=$cmd (available: ${handlers.keys})" }
                connection.close()
                return
            }
            logger.info { "ChannelSelectorService: dispatching cmd=$cmd to ${handler::class.simpleName}" }
            handler.income(channel = connection, buffer = buff)
        } catch (e: Exception) {
            logger.error(e) { "ChannelSelectorService: error processing channel" }
            throw e
        }
    }
}
