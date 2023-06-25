package pw.binom.proxy.handlers

import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.proxy.Urls
import pw.binom.strong.inject
import pw.binom.url.toPath
import pw.binom.url.toPathMask

class ExternalHandler : HttpHandler {
    private val clientControlHandler by inject<ClientControlHandler>()
    private val clientTransportHandler by inject<ClientTransportHandler>()
    private val logger by Logger.ofThisOrGlobal
    override suspend fun handle(exchange: HttpServerExchange) {
        val path = exchange.requestURI.path
        when {
            path.isMatch(Urls.CONTROL) -> clientControlHandler.handle(exchange)
            path.isMatch(Urls.TRANSPORT) -> clientTransportHandler.handle(exchange)
        }
        logger.info("path.isMatch(Urls.TRANSPORT) -> ${path.isMatch(Urls.TRANSPORT)}")
        logger.info("path=$path")
        logger.info("Urls.TRANSPORT=${Urls.TRANSPORT}")
        if (!exchange.responseStarted) {
            exchange.startResponse(404)
        }
    }
}