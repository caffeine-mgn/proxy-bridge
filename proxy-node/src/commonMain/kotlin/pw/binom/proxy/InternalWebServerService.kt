package pw.binom.proxy

import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServer2
import pw.binom.proxy.handlers.ProxyHandler
import pw.binom.strong.inject

class InternalWebServerService : AbstractWebServerService() {
    private val properties by inject<RuntimeProperties>()
    private val proxyHandler by inject<ProxyHandler>()
    override val handler: HttpHandler
        get() = proxyHandler

    override fun bind(server: HttpServer2) {
        properties.internalBinds.forEach {
            server.listen(it)
        }
    }
}