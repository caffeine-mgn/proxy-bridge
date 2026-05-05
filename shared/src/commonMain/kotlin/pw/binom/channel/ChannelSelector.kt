package pw.binom.channel

import io.github.oshai.kotlinlogging.KotlinLogging
import io.klogging.logger
import io.ktor.network.selector.SelectorManager
import org.koin.core.component.KoinComponent
import org.koin.dsl.module
import pw.binom.multiplexer.DuplexChannel

object ChannelSelector : KoinComponent {
    val module = module {
        single { ChannelSelector }
    }
    private val logger = KotlinLogging.logger { }
    private val handlers by lazy { getKoin().getAll<ChannelHandler>().associateBy { it.id } }

    suspend fun processing(connection: DuplexChannel, selector: SelectorManager) {
        try {
            println("Reading package")
            val buff = connection.receive()
            println("Income ${buff.size} bytes")
            val cmd = buff.readByte()

            val handler = handlers[cmd]
            if (handler == null) {
                logger.warn { "Unknown command $cmd" }
                connection.close()
                return
            }
            logger.info { "Handler: ${handler::class}" }
            handler.income(selector = selector, channel = connection, buffer = buff)
//        when (cmd) {
//            TcpConnectChannel.ID -> TcpConnectChannel.income(selector = selector, channel = connection, buffer = buff)
//            FileChannel.ID -> FileChannel.income(selector = selector, channel = connection, buffer = buff)
//            else -> {
//                logger.warn { "Unknown command $cmd" }
//                connection.close()
//            }
//        }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
