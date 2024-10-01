package pw.binom.proxy.controllers

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import pw.binom.io.http.Headers
import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.proxy.dto.ChannelStateInfo
import pw.binom.proxy.services.ServerControlService
import pw.binom.strong.inject

/**
 * Возвращает информацию о текущих соединениях
 */
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
