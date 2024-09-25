package pw.binom.proxy.server.handlers

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
import pw.binom.proxy.ChannelId
import pw.binom.proxy.Urls
import pw.binom.proxy.channels.WSSpitChannel
import pw.binom.proxy.server.ClientService
import pw.binom.proxy.server.properties.RuntimeClientProperties
import pw.binom.proxy.server.services.ServerControlService
import pw.binom.strong.inject

class ClientTransportWsHandler : HttpHandler, MetricProvider {
    private val clientService by inject<ClientService>()
    private val runtimeClientProperties by inject<RuntimeClientProperties>()
    private val logger by Logger.ofThisOrGlobal
    private val metricProvider = MetricProviderImpl()
    override val metrics: List<MetricUnit> by metricProvider
    private val controlService by inject<ServerControlService>()
    private val controlConnectionCounter = metricProvider.gaugeLong(name = "ws_transport")
    private val connectError = metricProvider.counterLong(name = "ws_transport_error")

    override suspend fun handle(exchange: HttpServerExchange) {
        val id = exchange.getPathVariables(Urls.TRANSPORT_WS)["id"]?.toIntOrNull()
//        val id = exchange.requestURI.query?.find("id")?.toIntOrNull()
        if (id == null) {
            exchange.startResponse(401)
            connectError.inc()
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
            exchange.startResponse(401)
            return
        }
        if (exchange.requestMethod != "GET") {
            connectError.inc()
            exchange.startResponse(401)
            return
        }

        controlConnectionCounter.inc()
        try {
            val channel = WSSpitChannel(
                id = ChannelId(id),
                connection = exchange.acceptWebsocket(bufferSize = runtimeClientProperties.bufferSize),
                logger = Logger.getLogger("SERVER")
            )
            controlService.newChannel(channel)
            channel.awaitClose()
        } finally {
            controlConnectionCounter.dec()
        }
        /*        return

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
                }*/
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
