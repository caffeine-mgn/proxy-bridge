package pw.binom.proxy.services

import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServer2
import pw.binom.logger.Logger
import pw.binom.logger.infoSync
import pw.binom.proxy.controllers.InternalHandler
import pw.binom.proxy.properties.ProxyProperties
import pw.binom.strong.inject

class InternalWebServerService : AbstractWebServerService() {
    private val properties by inject<ProxyProperties>()
    private val internalHandler by inject<InternalHandler>()
    private val logger by Logger.ofThisOrGlobal
    override val handler: HttpHandler
        get() = internalHandler
    var port = 0
        private set

    override fun bind(server: HttpServer2) {
        properties.internalBinds.forEach {
            val l = server.listen(it)
            port = l.port
            logger.infoSync("Bind internal to ${it.host}:${l.port}")
        }
    }
}
