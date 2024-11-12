package pw.binom.proxy.controllers

import kotlinx.coroutines.withTimeoutOrNull
import pw.binom.FrameAsyncChannelAdapter
import pw.binom.exceptions.TimeoutException
import pw.binom.io.AsyncChannel
import pw.binom.io.http.HashHeaders
import pw.binom.io.http.Headers
import pw.binom.io.http.forEachHeader
import pw.binom.io.http.headersOf
import pw.binom.io.httpClient.BaseHttpClient
import pw.binom.io.httpClient.ConnectionFactory
import pw.binom.io.httpClient.OutputLength
import pw.binom.io.httpClient.RequestHook
import pw.binom.io.httpClient.protocol.ConnectFactory2
import pw.binom.io.httpClient.protocol.ProtocolSelector
import pw.binom.io.httpClient.protocol.ProtocolSelectorBySchema
import pw.binom.io.httpClient.protocol.v11.Http11ConnectFactory2
import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.io.useAsync
import pw.binom.logger.Logger
import pw.binom.logger.debug
import pw.binom.logger.info
import pw.binom.metric.MetricProvider
import pw.binom.metric.MetricProviderImpl
import pw.binom.metric.MetricUnit
import pw.binom.network.NetworkManager
import pw.binom.proxy.server.ClientService
import pw.binom.proxy.server.ProxedFactory
import pw.binom.proxy.properties.ProxyProperties
import pw.binom.proxy.exceptions.ClientMissingException
import pw.binom.proxy.io.copyTo
import pw.binom.services.VirtualChannelService
import pw.binom.strong.inject
import pw.binom.subchannel.TcpExchange
import pw.binom.subchannel.WorkerChanelClient
import pw.binom.url.URL
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class ProxyHandler : HttpHandler, MetricProvider {
//    private val clientService by inject<ClientService>()
    private val networkManager by inject<NetworkManager>()
    private val runtimeProperties by inject<ProxyProperties>()

    //    private val controlService by inject<ServerControlService>()
//    private val gatewayClient by inject<GatewayClient>()
    private val virtualChannelService by inject<VirtualChannelService>()
    private val logger by Logger.ofThisOrGlobal
    private val metricProvider = MetricProviderImpl()
    override val metrics: List<MetricUnit> by metricProvider
    private val connectCount = metricProvider.gaugeLong(name = "proxy_connect_count")
//    private val tcpCommunicatePair by inject<TcpCommunicatePair>()

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
                        val channel = connect(
                            host = url.host.domain,
                            port = url.host.port ?: 80,
                        )
                        FrameAsyncChannelAdapter(channel.channel)
//                        FrameAsyncChannelAdapter(TcpCommunicatePair.serverHandshake(channel.channel))
                    })
            )

        BaseHttpClient(
            useKeepAlive = false,
            protocolSelector = protocolSelector,
            requestHook = RequestHook.Default
        )
    }

    private suspend fun httpRequest(exchange: HttpServerExchange) {
        logger.info("Http request!!!")
        logger.info("#1")
        val req =
            withTimeoutOrNull(10.seconds) {
                val newHeaders = HashHeaders()
                exchange.requestHeaders.forEachHeader { k, v ->
                    println("$k: $v")
                }
                newHeaders.addAll(exchange.requestHeaders.toSimpleHeaders())
                newHeaders[Headers.CONNECTION] = "Close"
                httpClient.startConnect(
                    method = exchange.requestMethod,
                    uri = exchange.requestURI.toURL(),
                    headers = newHeaders,
                    requestLength = OutputLength.None
                )
            }
        logger.info("#2")
        if (req == null) {
            logger.info("Can't connect to remote http server")
            exchange.startResponse(500)
            logger.info("#3")
            return
        }
        logger.info("#4")
        if (exchange.requestHeaders.bodyExist) {
            logger.info("#5")
            logger.info("Copping http->ws")
            req.startWriteBinary().useAsync { output ->
                exchange.input.copyTo(output, bufferSize = runtimeProperties.bufferSize) {
                    logger.debug("http->ws $it")
                }
                output.flush()
            }
            logger.info("#6")
            logger.info("Request data sent!")
        }
        logger.info("#7")
        val resp = req.flush()
        logger.info("#8")
        exchange.startResponse(
            statusCode = resp.responseCode,
            headers = resp.inputHeaders + headersOf(Headers.PROXY_CONNECTION to Headers.KEEP_ALIVE)
        )
        logger.info("#9")
        if (resp.inputHeaders.bodyExist) {
            logger.info("#10")
            logger.info("Copping ws->http")
            resp.readBinary().useAsync { input ->
                logger.info("Input type: $input. available: ${input.available}")
                exchange.output.useAsync { output ->
                    input.copyTo(output, bufferSize = runtimeProperties.bufferSize) {
//                        logger.debug("ws->http $it")
                    }
                }
            }
            logger.info("#11")
            logger.info("Response data sent!")
        }
        logger.info("#12")
        logger.info("Response finished!")

    }

    private suspend fun tcp(exchange: HttpServerExchange) {
        logger.info("User connected")
        val items = exchange.requestURI.toString().split(':', limit = 2)
        val host = items[0]
        val port = items[1].toInt()
        val compressLevel = runtimeProperties.tcpCompressLevel
        val channel =
            try {
                connect(
                    host = host,
                    port = port,
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
        connectCount.inc()
        try {
            incomeChannel.useAsync { incomeChannel ->
                channel.start(incomeChannel)
            }
            logger.info("Proxy finished!")
        } catch (e: Throwable) {
            throw e
        } finally {
            connectCount.dec()
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

    /**
     * Возвращает новый транспортный поток. В случае успеха помечает поток как занятый.
     */
    suspend fun connect(
        host: String,
        port: Int,
    ): TcpExchange {
        val channel = this.virtualChannelService.createChannel()
        try {
            return WorkerChanelClient(channel).useAsync { worker ->
                worker.startTcp(
                    host = host,
                    port = port,
                )
            }
        } catch (e: Throwable) {
            channel.asyncCloseAnyway()
            throw e
        }
        /*
        val channel = getOrCreateIdleChannel()
        gatewayClientService.sendCmd(
            ControlRequestDto(
                proxyConnect = ControlRequestDto.ProxyConnect(
                    id = channel.id,
                    host = host,
                    port = port,
                    compressLevel = compressLevel,
                )
            )
        )
        channel.locked.synchronize {
            channel.touch()
            channel.description = "$host:$port"
            channel.isBusy = true
        }
        try {
            logger.info("Wait until gateway connect to $host:$port...")
            logger.info("Gateway connected to $host:$port!")
            channel.isBusy = true
            return CloseWaterFrameChannel(channel.channel) {
                returnChannelToPool(channel)
            }//.withLogger("$host:$port")
        } catch (e: Throwable) {
            logger.info("Gateway can't connect to $host:$port!")
            channel.locked.synchronize {
                channel.isBusy = false
            }
            throw e
        }
        */
    }
}
