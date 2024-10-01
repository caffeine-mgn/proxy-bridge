package pw.binom.proxy.controllers

import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.logger.Logger
import pw.binom.Urls
import pw.binom.strong.inject

/**
 * Принимает входящие подключение из внешней сети
 */
class ExternalHandler : HttpHandler {
    private val clientControlHandler by inject<ClientControlHandler>()
    private val clientTransportTcpHandler by inject<ClientTransportTcpHandler>()
    private val clientTransportWsHandler by inject<ClientTransportWsHandler>()
    private val clientPoolInputHandler by inject<ClientPoolInputHandler>()
    private val clientPoolOutputHandler by inject<ClientPoolOutputHandler>()
    private val logger by Logger.ofThisOrGlobal
    override suspend fun handle(exchange: HttpServerExchange) {
        val path = exchange.requestURI.path
        when {
            path.isMatch(Urls.CONTROL) -> clientControlHandler.handle(exchange)
            path.isMatch(Urls.TRANSPORT_TCP) -> clientTransportTcpHandler.handle(exchange)
            path.isMatch(Urls.TRANSPORT_WS) -> clientTransportWsHandler.handle(exchange)
            path.isMatch(Urls.TRANSPORT_LONG_POOLING_CLIENT_WRITE) -> clientPoolInputHandler.handle(exchange)
            path.isMatch(Urls.TRANSPORT_LONG_POOLING_NODE_WRITE) -> clientPoolOutputHandler.handle(exchange)
        }
        if (!exchange.responseStarted) {
            exchange.startResponse(404)
        }
    }
}
