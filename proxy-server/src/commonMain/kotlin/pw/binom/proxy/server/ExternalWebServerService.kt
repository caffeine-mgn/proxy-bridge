package pw.binom.proxy.server

import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServer2
import pw.binom.logger.Logger
import pw.binom.logger.infoSync
import pw.binom.proxy.server.handlers.ExternalHandler
import pw.binom.proxy.server.properties.RuntimeClientProperties
import pw.binom.strong.inject

class ExternalWebServerService : AbstractWebServerService() {
    private val logger by Logger.ofThisOrGlobal

    private val properties by inject<RuntimeClientProperties>()

    override val handler: HttpHandler
        get() = externalHandler

    var port = 0
        private set

    override fun bind(server: HttpServer2) {
        properties.externalBinds.forEach {
            val l = server.listen(it)
            port = l.port
            logger.infoSync("Bind external to ${it.host}:${l.port}")
        }
    }

    private val externalHandler by inject<ExternalHandler>()
}
