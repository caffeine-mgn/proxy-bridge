package pw.binom.proxy.controllers

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
import pw.binom.Urls
import pw.binom.proxy.channels.WSSpitChannel
import pw.binom.proxy.server.ClientService
import pw.binom.proxy.properties.ProxyProperties
import pw.binom.proxy.services.ServerControlService
import pw.binom.strong.inject

class ClientTransportWsHandler : HttpHandler, MetricProvider {
    private val clientService by inject<ClientService>()
    private val proxyProperties by inject<ProxyProperties>()
    private val logger by Logger.ofThisOrGlobal
    private val metricProvider = MetricProviderImpl()
    override val metrics: List<MetricUnit> by metricProvider
    private val controlService by inject<ServerControlService>()
    private val controlConnectionCounter = metricProvider.gaugeLong(name = "ws_transport")
    private val connectError = metricProvider.counterLong(name = "ws_transport_error")
    private val retryChannelCounter = metricProvider.counterLong(name = "ws_transport_retry")

    override suspend fun handle(exchange: HttpServerExchange) {
        val id = exchange.requestHeaders["X-trace"]?.firstOrNull()
//        val id = exchange.getPathVariables(Urls.TRANSPORT_WS)["id"]
//        val id = exchange.requestURI.query?.find("id")?.toIntOrNull()
        if (id == null) {
            logger.info("Missing id")
            logger.info("Method: ${exchange.requestMethod} ${exchange.requestURI}")
            logger.info("Headers:")
            exchange.requestHeaders.forEachHeader { key, value ->
                logger.info("  $key: $value")
            }
            exchange.startResponse(404)
            connectError.inc()
            return
        }
        val upgrade = exchange.requestHeaders[Headers.UPGRADE]?.lastOrNull()
        if (!upgrade.equals(Headers.WEBSOCKET, ignoreCase = true)) {
            retryChannelCounter.inc()
            logger.info("Invalid web socket header!")
            logger.info("Method: ${exchange.requestMethod} ${exchange.requestURI}")
            logger.info("Headers:")
            exchange.requestHeaders.forEachHeader { key, value ->
                logger.info("  $key: $value")
            }
            exchange.response().also {
                it.status = 200
                it.send("OK")
            }
//            exchange.startResponse(200)
            controlService.channelFailShot(ChannelId(id))
            return
        }
        if (exchange.requestMethod != "GET") {
            connectError.inc()
            exchange.startResponse(401)
            controlService.channelFailShot(ChannelId(id))
            return
        }

        controlConnectionCounter.inc()
        try {
            val channel = WSSpitChannel(
                id = ChannelId(id),
                connection = exchange.acceptWebsocket(bufferSize = proxyProperties.bufferSize),
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
