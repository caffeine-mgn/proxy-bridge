package pw.binom.proxy.controllers

import pw.binom.exceptions.TimeoutException
import pw.binom.gateway.GatewayClient
import pw.binom.io.AsyncChannel
import pw.binom.io.http.Headers
import pw.binom.io.http.headersOf
import pw.binom.io.httpClient.BaseHttpClient
import pw.binom.io.httpClient.ConnectionFactory
import pw.binom.io.httpClient.RequestHook
import pw.binom.io.httpClient.protocol.ConnectFactory2
import pw.binom.io.httpClient.protocol.ProtocolSelector
import pw.binom.io.httpClient.protocol.ProtocolSelectorBySchema
import pw.binom.io.httpClient.protocol.v11.Http11ConnectFactory2
import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.metric.MetricProvider
import pw.binom.metric.MetricProviderImpl
import pw.binom.metric.MetricUnit
import pw.binom.network.NetworkManager
import pw.binom.proxy.behaviors.TcpBridgeBehavior
import pw.binom.proxy.server.ClientService
import pw.binom.proxy.server.ProxedFactory
import pw.binom.proxy.properties.ProxyProperties
import pw.binom.proxy.exceptions.ClientMissingException
import pw.binom.proxy.services.ServerControlService
import pw.binom.strong.inject
import pw.binom.url.URL

class ProxyHandler : HttpHandler, MetricProvider {
    private val clientService by inject<ClientService>()
    private val networkManager by inject<NetworkManager>()
    private val runtimeProperties by inject<ProxyProperties>()
    private val controlService by inject<ServerControlService>()
    private val gatewayClient by inject<GatewayClient>()
    private val logger by Logger.ofThisOrGlobal
    private val metricProvider = MetricProviderImpl()
    override val metrics: List<MetricUnit> by metricProvider
    private val connectCount = metricProvider.gaugeLong(name = "proxy_connect_count")

    override suspend fun handle(exchange: HttpServerExchange) {
        when (exchange.requestMethod) {
            "CONNECT" -> tcp(exchange)
            else -> httpRequest(exchange)
        }
    }

    class SingleProtocolSelector(val factory: ConnectFactory2) : ProtocolSelector {
        override fun find(url: URL) = factory
    }

    val httpClient by lazy {
        val baseProtocolSelector = ProtocolSelectorBySchema()
        val http = Http11ConnectFactory2(networkManager = networkManager, connectFactory = ConnectionFactory.DEFAULT)
        baseProtocolSelector.set(
            http,
            "http",
            "ws"
        )
        val protocolSelector =
            SingleProtocolSelector(
                ProxedFactory(protocolSelector = baseProtocolSelector,
                    channelProvider = { url ->
                        clientService.connectTo(
                            host = url.host.domain,
                            port = url.host.port ?: 80
                        ).second
                    })
            )

        BaseHttpClient(
            useKeepAlive = true,
            protocolSelector = protocolSelector,
            requestHook = RequestHook.Default
        )
    }

    private suspend fun httpRequest(exchange: HttpServerExchange) {
        TODO()
//        val req =
//            try {
//                httpClient.startConnect(
//                    method = exchange.requestMethod,
//                    uri = exchange.requestURI.toURL(),
//                    headers = exchange.requestHeaders,
//                    requestLength = OutputLength.None
//                )
//            } catch (e: ClientMissingException) {
//                exchange.startResponse(503)
//                return
//            }
//        if (exchange.requestHeaders.bodyExist) {
//            logger.info("Copping http->ws")
//            req.startWriteBinary().useAsync { output ->
//                exchange.input.copyTo(output, bufferSize = runtimeProperties.bufferSize) {
//                    logger.debug("http->ws $it")
//                }
//                output.flush()
//            }
//            logger.info("Request data sent!")
//        }
//        val resp = req.flush()
//        exchange.startResponse(
//            statusCode = resp.responseCode,
//            headers = resp.inputHeaders + headersOf(Headers.PROXY_CONNECTION to Headers.KEEP_ALIVE)
//        )
//        if (resp.inputHeaders.bodyExist) {
//            logger.info("Copping ws->http")
//            resp.readBinary().useAsync { input ->
//                logger.info("Input type: $input. available: ${input.available}")
//                exchange.output.useAsync { output ->
//                    input.copyTo(output, bufferSize = runtimeProperties.bufferSize) {
//                        logger.debug("ws->http $it")
//                    }
//                }
//            }
//            logger.info("Response data sent!")
//        }
//        logger.info("Response finished!")
//
    }

    private suspend fun tcp(exchange: HttpServerExchange) {
        logger.info("User connected")
        val items = exchange.requestURI.toString().split(':', limit = 2)
        val host = items[0]
        val port = items[1].toInt()
        val compressLevel = runtimeProperties.tcpCompressLevel
        val channel =
            try {
                controlService.connect(
                    host = host,
                    port = port,
                    compressLevel = compressLevel,
                )
            } catch (e: TimeoutException) {
                logger.info("Timeout connect to $host:$port")
                exchange.startResponse(504, headersOf(Headers.CONNECTION to Headers.CLOSE))
                return
            } catch (e: ClientMissingException) {
                exchange.startResponse(404, headersOf(Headers.CONNECTION to Headers.CLOSE))
                return
            } catch (e: Throwable) {
                logger.info(text = "Can't connect to $host:$port", exception = e)
                exchange.startResponse(500, headersOf(Headers.CONNECTION to Headers.CLOSE))
                return
            }
        logger.info("Channel connected! Try return code 200")
        exchange.startResponse(200, headersOf(Headers.CONNECTION to Headers.CLOSE))
        val incomeChannel =
            AsyncChannel.create(
                input = exchange.input,
                output = exchange.output
            )
        val behavior = TcpBridgeBehavior.create(
            from = channel,
            tcp = incomeChannel,
            client = gatewayClient,
            host = host,
            port = port,
            compressLevel = compressLevel,
        )
        connectCount.inc()
        try {
            controlService.assignBehavior(channel = channel, behavior = behavior)
        } catch (e: Throwable) {
            connectCount.dec()
            throw e
        }
        try {
            behavior.run()
        } finally {
            connectCount.dec()
            controlService.sendToPool(channel)
        }
        /*
        TcpBridgeBehavior.create()
        try {
            val channelStopped = channel.connectWith(
                other = incomeChannel,
                bufferSize = DEFAULT_BUFFER_SIZE,
            ).start()
            println("channelStopped=$channelStopped")
            if (channelStopped) {
                println("Sending channel to pool")
                controlService.sendToPool(channel)
            }
            incomeChannel.flush()
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
        } finally {
            logger.info("proxy request finished!")
        }
        */
    }
}
