package pw.binom.proxy.node.handlers

import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.proxy.node.PrometheusController
import pw.binom.strong.inject

class InternalHandler : HttpHandler {
    private val proxyHandler by inject<ProxyHandler>()
    private val prometheusController by inject<PrometheusController>()

    override suspend fun handle(exchange: HttpServerExchange) {
        if (exchange.requestMethod == "CONNECT" || ":" in exchange.requestURI.toString()) {
            proxyHandler.handle(exchange)
        }
        if (exchange.requestMethod == "GET" && exchange.requestURI.toString() == "/metrics") {
            prometheusController.handle(exchange)
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
