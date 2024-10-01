package pw.binom.proxy.controllers

import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.Urls
import pw.binom.proxy.server.ClientService
import pw.binom.strong.inject

class ClientPoolInputHandler : HttpHandler {
//    private val clientPoolOutputHandler by inject<ClientPoolOutputHandler>()
private val clientService by inject<ClientService>()
    private val logger by Logger.ofThisOrGlobal
    override suspend fun handle(exchange: HttpServerExchange) {
        val id = exchange.requestURI.path.getVariable("id", Urls.TRANSPORT_LONG_POOLING_CLIENT_WRITE)
            ?.toInt()
        if (id == null) {
            logger.info("Invalid id")
            throw IllegalArgumentException("invalid id")
        }
        logger.info("Client connected. Pass control to io thread")
        clientService.inputConnected(
            id=id,
            connection = {exchange.input}
        )
//        clientPoolOutputHandler.inputReady(
//            id = id,
//            input = exchange.input,
//        )
    }
}
