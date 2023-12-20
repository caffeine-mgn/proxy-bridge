package pw.binom.proxy.node

import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServer2
import pw.binom.logger.Logger
import pw.binom.logger.infoSync
import pw.binom.proxy.node.handlers.ExternalHandler
import pw.binom.strong.inject

class ExternalWebServerService : AbstractWebServerService() {
    private val logger by Logger.ofThisOrGlobal

    private val properties by inject<RuntimeProperties>()

    override val handler: HttpHandler
        get() = externalHandler

    override fun bind(server: HttpServer2) {
        properties.externalBinds.forEach {
            server.listen(it)
            logger.infoSync("Bind external to $it")
        }
    }

    private val externalHandler by inject<ExternalHandler>()
}
