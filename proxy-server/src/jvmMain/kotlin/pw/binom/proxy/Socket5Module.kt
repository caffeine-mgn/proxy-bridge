package pw.binom.proxy

import io.ktor.network.selector.SelectorManager
import org.koin.dsl.module
import org.koin.dsl.onClose

fun Sock5ProxyModule(port: Int) = module {
    single(createdAtStart = true) {
        Sock5ProxyService(
            selector = get(),
            handler = get(),
            port = port,
        )
    }.onClose { it?.close() }
}

class Sock5ProxyService(
    selector: SelectorManager,
    handler: ConnectProcessing,
    port: Int,
    bind: String = "0.0.0.0",
) : AutoCloseable {
    val proxy = Socks5Server(
        port = port,
        selectorManager = selector,
        authProvider = null,
        onConnect = handler,
        bind = bind,
    )
    override fun close() {
        proxy.close()
    }
}
