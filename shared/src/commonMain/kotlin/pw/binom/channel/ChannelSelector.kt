package pw.binom.channel

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.selector.SelectorManager
import org.koin.core.component.KoinComponent
import org.koin.dsl.module
import pw.binom.multiplexer.DuplexChannel

class ChannelSelector : KoinComponent {
    companion object {
        val module = module {
            single { ChannelSelector() }
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
                logger.warn { "Unknown command $cmd" }
                connection.close()
                return
            }
            handler.income(channel = connection, buffer = buff)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
