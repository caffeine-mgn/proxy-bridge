package pw.binom.proxy

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.selector.*
import org.koin.dsl.module
import org.koin.dsl.onClose
import pw.binom.channel.TcpConnectChannel
import pw.binom.http.HttpProxy
import pw.binom.multiplexer.MultiplexerHolder
import pw.binom.utils.connect

fun HttpProxyModule(port: Int) = module {
    single(createdAtStart = true) {
        HttpProxyService(
            selector = get(),
            handler = get(),
            port = port,
        )
    }.onClose { it?.close() }
}

class HttpProxyService(
    selector: SelectorManager,
    handler: ConnectProcessing,
    port: Int,
) : AutoCloseable {
    val logger = KotlinLogging.logger {}

    init {
        logger.info { "Starting Http Proxy on 0.0.0.0:$port" }
    }

    val proxy = HttpProxy(
        port = port,
        selector = selector,
        onConnect = handler,
        onHttp = { _, _, _, _ -> }
    )

    override fun close() {
        proxy.close()
    }

}
