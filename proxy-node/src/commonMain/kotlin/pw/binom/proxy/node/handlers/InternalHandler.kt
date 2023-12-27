package pw.binom.proxy.node.handlers

import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.proxy.node.ClientService
import pw.binom.strong.inject

class InternalHandler : HttpHandler {
    private val proxyHandler by inject<ProxyHandler>()
    private val clientService by inject<ClientService>()
    override suspend fun handle(exchange: HttpServerExchange) {
        if (exchange.requestMethod == "CONNECT" || ":" in exchange.requestURI.toString()) {
            proxyHandler.handle(exchange)
        }

//        if (exchange.requestURI.isMatch("/files/put")) {
//            val path = exchange.getQueryParams()["path"] ?: throw IllegalArgumentException("File path not passed")
//            clientService.putFile(
//                path = path,
//                input = exchange.input,
//            )
//
//            exchange.startResponse(200)
//        }
    }
}
