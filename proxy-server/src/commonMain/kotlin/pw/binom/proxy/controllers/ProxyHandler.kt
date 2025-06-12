package pw.binom.proxy.controllers

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import pw.binom.FrameAsyncChannelAdapter
import pw.binom.http.client.Http11ClientExchange
import pw.binom.http.client.HttpClientRunnable
import pw.binom.http.client.factory.Http11ConnectionFactory
import pw.binom.http.client.factory.HttpProxyNetSocketFactory
import pw.binom.http.client.factory.Https11ConnectionFactory
import pw.binom.http.client.factory.NativeNetChannelFactory
import pw.binom.http.client.factory.NetSocketFactory
import pw.binom.io.AsyncChannel
import pw.binom.io.http.HashHeaders
import pw.binom.io.http.Headers
import pw.binom.io.http.forEachHeader
import pw.binom.io.http.headersOf
import pw.binom.io.httpClient.BaseHttpClient
import pw.binom.io.httpClient.ConnectionFactory
import pw.binom.io.httpClient.RequestHook
import pw.binom.io.httpClient.ResponseLength
import pw.binom.io.httpClient.protocol.ConnectFactory2
import pw.binom.io.httpClient.protocol.ProtocolSelector
import pw.binom.io.httpClient.protocol.ProtocolSelectorBySchema
import pw.binom.io.httpClient.protocol.ssl.HttpSSLConnectFactory2
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
import pw.binom.proxy.HttpsConverterChannel
import pw.binom.proxy.server.ProxedFactory
import pw.binom.proxy.properties.ProxyProperties
import pw.binom.proxy.exceptions.ClientMissingException
import pw.binom.proxy.io.copyTo
import pw.binom.services.ClientService
import pw.binom.services.VirtualChannelService
import pw.binom.ssl.KeyManager
import pw.binom.strong.inject
import pw.binom.subchannel.TcpExchange
import pw.binom.subchannel.WorkerChanelClient
import pw.binom.subchannel.commands.TcpConnectCommand
import pw.binom.url.URL
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class ProxyHandler : HttpHandler, MetricProvider {
    //    private val clientService by inject<ClientService>()
    private val networkManager by inject<NetworkManager>()
    private val runtimeProperties by inject<ProxyProperties>()
    private val clientService by inject<ClientService>()
    private val tcpConnectCommand by inject<TcpConnectCommand>()

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

    class VirtualSocketFactory(
        val tcpConnectCommand: TcpConnectCommand
    ) : NetSocketFactory {
        override suspend fun connect(host: String, port: Int): AsyncChannel {
            val channel = tcpConnectCommand.connect(
                host = host,
                port = port,
            )
            return FrameAsyncChannelAdapter(channel.channel())
        }

    }

    val httpClient2 by lazy {
        HttpClientRunnable(
            factory = Https11ConnectionFactory(
                fallback = Http11ConnectionFactory(),
            ),
            source = VirtualSocketFactory(tcpConnectCommand)
        )
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
                ProxedFactory(
                    protocolSelector = baseProtocolSelector,
                    channelProvider = { url ->
                        val channel = connect(
                            host = url.host.domain,
                            port = url.host.port ?: 80,
                        )
                        FrameAsyncChannelAdapter(channel.channel())
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
        val req =
            withTimeoutOrNull(10.seconds) {
                val newHeaders = HashHeaders()
                newHeaders.addAll(exchange.requestHeaders.toSimpleHeaders())
                newHeaders[Headers.CONNECTION] = "Close"
                var remoteUrl = exchange.requestURI.toURL()
                println("URL: ${remoteUrl.host}:${remoteUrl.port}")
                if (remoteUrl.domain == "nexus.isb" && (remoteUrl.port == 80 || remoteUrl.port == null)) {
                    remoteUrl = remoteUrl.copy(schema = "https", port = 443)
                    println("NEW URL: $remoteUrl")
                }
                httpClient2.connect(
                    method = exchange.requestMethod,
                    url = remoteUrl,
                    headers = newHeaders
                ) as Http11ClientExchange
//                httpClient.startConnect(
//                    method = exchange.requestMethod,
//                    uri = remoteUrl,
//                    headers = newHeaders,
//                    requestLength = ResponseLength.None
//                )
            }
        if (req == null) {
            logger.info("Can't connect to remote http server")
            exchange.startResponse(500)
            return
        }
        if (exchange.requestHeaders.bodyExist) {
            req.getOutput().useAsync { output ->
                exchange.input.copyTo(output, bufferSize = runtimeProperties.bufferSize) {
                }
                output.flush()
            }
        }
        val responseHeaders = req.getResponseHeaders()
        val addResponseHeaders = headersOf(Headers.PROXY_CONNECTION to Headers.KEEP_ALIVE)
        val newReponseHeaders = responseHeaders + addResponseHeaders
//        println("responseHeaders: $responseHeaders")
//        println("addResponseHeaders: $addResponseHeaders")
//        println("newReponseHeaders: $newReponseHeaders")
        exchange.startResponse(
            statusCode = req.getResponseCode(),
            headers = newReponseHeaders
        )
        if (req.getResponseHeaders().bodyExist) {
            req.getInput().useAsync { input ->
                exchange.output.useAsync { output ->
                    input.copyTo(output, bufferSize = runtimeProperties.bufferSize) {
//                        logger.debug("ws->http $it")
                    }
                }
            }
        }
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
            } catch (e: TimeoutCancellationException) {
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
        var incomeChannel =
            AsyncChannel.create(
                input = exchange.input,
                output = exchange.output
            )

        if (host == "nexus.isb" && port == 80) {
            logger.info("Connect to $host:$port with HTTPS converting")
            incomeChannel = HttpsConverterChannel(
                source = incomeChannel,
                host = host,
                port = 443,
            )
        } else {
            logger.info("Connect to $host:$port")
        }

        connectCount.inc()
        try {
            incomeChannel.useAsync { incomeChannel ->
                channel.startExchange(incomeChannel)
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
    ): TcpConnectCommand.Connected {
        return tcpConnectCommand.connect(
            host = host,
            port = port,
        )
        /*
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
        */
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
