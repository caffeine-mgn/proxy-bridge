package pw.binom.proxy.handlers

import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.io.httpServer.acceptWebsocket
import pw.binom.io.use

class ServerHandler : HttpHandler {
    override suspend fun handle(exchange: HttpServerExchange) {
        val connection = exchange.acceptWebsocket()
        while (true) {
            connection.read().use { msg ->

            }
        }
    }
}
