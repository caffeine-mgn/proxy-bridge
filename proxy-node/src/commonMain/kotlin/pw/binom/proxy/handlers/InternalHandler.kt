package pw.binom.proxy.handlers

import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.strong.inject

class InternalHandler : HttpHandler {
    private val proxyHandler by inject<ProxyHandler>()
    override suspend fun handle(exchange: HttpServerExchange) {
        if (exchange.requestMethod == "CONNECT" || ":" in exchange.requestURI.toString()) {
            proxyHandler.handle(exchange)
        }
    }
}