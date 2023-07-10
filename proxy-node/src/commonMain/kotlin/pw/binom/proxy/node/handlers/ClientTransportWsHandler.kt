package pw.binom.proxy.node.handlers

import pw.binom.io.AsyncChannel
import pw.binom.io.LazyAsyncInput
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.io.httpServer.acceptWebsocket
import pw.binom.proxy.node.ClientService
import pw.binom.proxy.Urls
import pw.binom.proxy.io.AsyncInputViaWebSocketMessage
import pw.binom.strong.inject

class ClientTransportWsHandler : HttpHandler {
    private val clientService by inject<ClientService>()
    override suspend fun handle(exchange: HttpServerExchange) {
        val id = exchange.getPathVariables(Urls.TRANSPORT_WS)["id"]?.toIntOrNull()
//        val id = exchange.requestURI.query?.find("id")?.toIntOrNull()
        if (id == null) {
            exchange.startResponse(400)
            return
        }
        clientService.webSocketConnected(
            id=id,
            connection = {
                AsyncInputViaWebSocketMessage(exchange.acceptWebsocket())
            }
        )
//        clientService.transportProcessing(id = id) {
//            val connection = exchange.acceptWebsocket()
//            val input = LazyAsyncInput { connection.read() }
//            val output = connection.write(MessageType.BINARY)
//            AsyncChannel.Companion.create(
//                input = input,
//                output = output,
//            ) {
//                input.asyncCloseAnyway()
//                output.asyncCloseAnyway()
//                connection.asyncCloseAnyway()
//            }
//        }
    }
}
