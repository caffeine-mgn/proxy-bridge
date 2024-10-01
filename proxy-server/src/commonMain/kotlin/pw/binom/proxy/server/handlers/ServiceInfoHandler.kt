package pw.binom.proxy.server.handlers

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import pw.binom.io.http.Headers
import pw.binom.io.http.headersOf
import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.proxy.server.dto.ChannelStateInfo
import pw.binom.proxy.server.services.ServerControlService
import pw.binom.strong.inject

class ServiceInfoHandler : HttpHandler {
    private val serverControlService by inject<ServerControlService>()
    override suspend fun handle(exchange: HttpServerExchange) {
        exchange.response().also {
            it.headers[Headers.CONTENT_TYPE] = "application/json"
            it.status = 200
            it.send(
                Json.encodeToString(
                    ListSerializer(ChannelStateInfo.serializer()),
                    serverControlService.getChannelsState()
                )
            )
        }
    }
}
