package pw.binom.proxy.node.handlers

import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.proxy.Urls
import pw.binom.strong.inject

class ExternalHandler : HttpHandler {
    private val clientControlHandler by inject<ClientControlHandler>()
    private val clientTransportTcpHandler by inject<ClientTransportTcpHandler>()
    private val clientTransportWsHandler by inject<ClientTransportWsHandler>()
    private val clientPoolInputHandler by inject<ClientPoolInputHandler>()
    private val clientPoolOutputHandler by inject<ClientPoolOutputHandler>()
    private val logger by Logger.ofThisOrGlobal
    override suspend fun handle(exchange: HttpServerExchange) {
        logger.info("Request!!! ${exchange.requestMethod} ${exchange.requestURI}")
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
