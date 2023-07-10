package pw.binom.proxy.node

import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServer2
import pw.binom.proxy.node.handlers.InternalHandler
import pw.binom.strong.inject

class InternalWebServerService : AbstractWebServerService() {
    private val properties by inject<RuntimeProperties>()
    private val internalHandler by inject<InternalHandler>()
    override val handler: HttpHandler
        get() = internalHandler

    override fun bind(server: HttpServer2) {
        properties.internalBinds.forEach {
            server.listen(it)
            println("Bind internal to $it")
        }
    }
}
