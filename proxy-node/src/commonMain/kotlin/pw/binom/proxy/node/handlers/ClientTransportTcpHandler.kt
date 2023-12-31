package pw.binom.proxy.node.handlers

import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.io.httpServer.acceptTcp
import pw.binom.proxy.node.ClientService
import pw.binom.proxy.Urls
import pw.binom.strong.inject

class ClientTransportTcpHandler : HttpHandler {
    private val clientService by inject<ClientService>()
    override suspend fun handle(exchange: HttpServerExchange) {
        val id = exchange.getPathVariables(Urls.TRANSPORT_TCP)["id"]?.toIntOrNull()
//        val id = exchange.requestURI.query?.find("id")?.toIntOrNull()
        if (id == null) {
            exchange.startResponse(400)
            return
        }
        clientService.webSocketConnected(
            id=id,
            connection = {exchange.acceptTcp()}
        )
//        clientService.transportProcessing(id = id) { exchange.acceptTcp() }
    }
}
