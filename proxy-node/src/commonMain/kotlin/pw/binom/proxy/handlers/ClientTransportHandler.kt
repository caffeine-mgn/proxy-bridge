package pw.binom.proxy.handlers

import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.io.httpServer.acceptTcp
import pw.binom.io.httpServer.acceptWebsocket
import pw.binom.proxy.ClientService
import pw.binom.strong.inject

class ClientTransportHandler : HttpHandler {
    private val clientService by inject<ClientService>()
    override suspend fun handle(exchange: HttpServerExchange) {
        val id = exchange.requestURI.query?.find("id")?.toIntOrNull()
        if (id == null) {
            exchange.startResponse(400)
            return
        }
        clientService.transportProcessing(id = id) { exchange.acceptTcp() }
    }
}