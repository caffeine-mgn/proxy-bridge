package pw.binom.proxy.handlers

import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.io.httpServer.acceptWebsocket

class ClientHandler : HttpHandler {
    override suspend fun handle(exchange: HttpServerExchange) {
        val connection = exchange.acceptWebsocket()
    }
}
