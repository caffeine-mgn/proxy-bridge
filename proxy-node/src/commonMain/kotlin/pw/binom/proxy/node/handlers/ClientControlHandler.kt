package pw.binom.proxy.node.handlers

import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.io.httpServer.acceptWebsocket
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.proxy.node.ClientConnection
import pw.binom.proxy.node.ClientService
import pw.binom.strong.inject

class ClientControlHandler : HttpHandler {
    private val clientService by inject<ClientService>()
    private val logger by Logger.ofThisOrGlobal
    private var clientCounter = 0
    override suspend fun handle(exchange: HttpServerExchange) {
        val connection = exchange.acceptWebsocket()
        val client = ClientConnection(
            connection = connection,
            Logger.getLogger("Proxy::Client #${++clientCounter}")
        )
        try {
            clientService.clientConnected(client)
            logger.info("Client connected!")
            client.processing()
        } catch (e: Throwable) {
            logger.info(text = "Client disconnected with error", exception = e)
        } finally {
            logger.info("Client disconnected!")
            clientService.clientDisconnected(client)
            client.asyncCloseAnyway()
        }
    }

}
