package pw.binom.proxy.server.handlers

import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.proxy.server.PrometheusController
import pw.binom.strong.inject

class InternalHandler : HttpHandler {
    private val proxyHandler by inject<ProxyHandler>()
    private val serviceInfoHandler by inject<ServiceInfoHandler>()
    private val prometheusController by inject<PrometheusController>()

    override suspend fun handle(exchange: HttpServerExchange) {
        if (exchange.requestMethod == "GET" && exchange.requestURI.toString() == "/service") {
            serviceInfoHandler.handle(exchange)
            return
        }
        if (exchange.requestMethod == "CONNECT" || ":" in exchange.requestURI.toString()) {
            proxyHandler.handle(exchange)
            return
        }
        if (exchange.requestMethod == "GET" && exchange.requestURI.toString() == "/metrics") {
            prometheusController.handle(exchange)
            return
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
