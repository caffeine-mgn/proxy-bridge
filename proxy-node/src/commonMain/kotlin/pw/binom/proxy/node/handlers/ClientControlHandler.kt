package pw.binom.proxy.node.handlers

import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.io.httpServer.acceptWebsocket
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.proxy.node.ClientConnection
import pw.binom.proxy.node.ClientService
import pw.binom.strong.Strong
import pw.binom.strong.inject

class ClientControlHandler : HttpHandler, Strong.DestroyableBean {
    private val clientService by inject<ClientService>()
    private val logger by Logger.ofThisOrGlobal
    private var clientCounter = 0
    private val clients = HashSet<WebSocketConnection>()
    override suspend fun handle(exchange: HttpServerExchange) {
        val connection = exchange.acceptWebsocket()
        val clientId = ++clientCounter
        val client = ClientConnection(
            connection = connection,
            logger = Logger.getLogger("Proxy::Client #$clientId"),
        )
        try {
            clientService.clientConnected(client)
            logger.info("Client $clientId connected!")
            clients += connection
            client.processing()
//            client.asyncCloseAnyway()
//            client.connection.asyncCloseAnyway()
        } catch (e: Throwable) {
            logger.info(text = "Client $clientId disconnected with error", exception = e)
        } finally {
            clients -= connection
            logger.info("Client $clientId disconnected!")
            clientService.clientDisconnected(client)
            client.asyncCloseAnyway()
        }
    }

    override suspend fun destroy(strong: Strong) {
        ArrayList(clients).forEach {
            it.asyncCloseAnyway()
        }
    }

}
