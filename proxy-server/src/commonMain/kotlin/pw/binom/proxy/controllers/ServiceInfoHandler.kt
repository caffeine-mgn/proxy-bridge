package pw.binom.proxy.controllers

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import pw.binom.io.http.Headers
import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.proxy.dto.ChannelStateInfo

/**
 * Возвращает информацию о текущих соединениях
 */
class ServiceInfoHandler : HttpHandler {
//    private val serverControlService by inject<ServerControlService>()
    private val json = Json {
        prettyPrint = true
    }

    override suspend fun handle(exchange: HttpServerExchange) {
        exchange.response().also {
            it.headers[Headers.CONTENT_TYPE] = "application/json"
            it.status = 200
            it.send(
                json.encodeToString(
                    ListSerializer(ChannelStateInfo.serializer()),
                    emptyList()
//                    serverControlService.getChannelsState()
                )
            )
        }
    }
}
