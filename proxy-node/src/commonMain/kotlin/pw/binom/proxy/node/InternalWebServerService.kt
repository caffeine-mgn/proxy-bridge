package pw.binom.proxy.node

import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServer2
import pw.binom.logger.Logger
import pw.binom.logger.infoSync
import pw.binom.proxy.node.handlers.InternalHandler
import pw.binom.strong.inject

class InternalWebServerService : AbstractWebServerService() {
    private val properties by inject<RuntimeProperties>()
    private val internalHandler by inject<InternalHandler>()
    private val logger by Logger.ofThisOrGlobal
    override val handler: HttpHandler
        get() = internalHandler

    override fun bind(server: HttpServer2) {
        properties.internalBinds.forEach {
            server.listen(it)
            logger.infoSync("Bind internal to $it")
        }
    }
}
