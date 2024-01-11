package pw.binom.proxy.node.handlers

import pw.binom.io.http.Headers
import pw.binom.io.http.forEachHeader
import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.io.httpServer.acceptWebsocket
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.metric.MetricProvider
import pw.binom.metric.MetricProviderImpl
import pw.binom.metric.MetricUnit
import pw.binom.proxy.Urls
import pw.binom.proxy.io.AsyncInputViaWebSocketMessage
import pw.binom.proxy.node.ClientService
import pw.binom.proxy.node.RuntimeClientProperties
import pw.binom.strong.inject

class ClientTransportWsHandler : HttpHandler, MetricProvider {
    private val clientService by inject<ClientService>()
    private val runtimeClientProperties by inject<RuntimeClientProperties>()
    private val logger by Logger.ofThisOrGlobal
    private val metricProvider = MetricProviderImpl()
    override val metrics: List<MetricUnit> by metricProvider
    private val controlConnectionCounter = metricProvider.gaugeLong(name = "ws_transport")
    private val connectError = metricProvider.counterLong(name = "ws_transport_error")

    override suspend fun handle(exchange: HttpServerExchange) {
        val id = exchange.getPathVariables(Urls.TRANSPORT_WS)["id"]?.toIntOrNull()
//        val id = exchange.requestURI.query?.find("id")?.toIntOrNull()
        if (id == null) {
            exchange.startResponse(400)
            return
        }
        val upgrade = exchange.requestHeaders[Headers.UPGRADE]?.lastOrNull()
        if (!upgrade.equals(Headers.WEBSOCKET, ignoreCase = true)) {
            connectError.inc()
            logger.info("Invalid web socket header!")
            logger.info("Method: ${exchange.requestMethod}")
            logger.info("Headers:")
            exchange.requestHeaders.forEachHeader { key, value ->
                logger.info("  $key: $value")
            }
            exchange.startResponse(400)
            return
        }
        if (exchange.requestMethod != "GET") {
        }
        try {
            controlConnectionCounter.inc()
            clientService.webSocketConnected(
                id = id,
                connection = {
                    AsyncInputViaWebSocketMessage(
                        exchange.acceptWebsocket(bufferSize = runtimeClientProperties.bufferSize)
                    )
                }
            )
        } finally {
            controlConnectionCounter.dec()
        }
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
