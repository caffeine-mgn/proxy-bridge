package pw.binom.proxy.node.handlers

import kotlinx.coroutines.NonCancellable.join
import pw.binom.io.AsyncChannel
import pw.binom.io.http.Headers
import pw.binom.io.http.emptyHeaders
import pw.binom.io.http.headersOf
import pw.binom.io.httpClient.BaseHttpClient
import pw.binom.io.httpClient.ConnectionFactory
import pw.binom.io.httpClient.OutputLength
import pw.binom.io.httpClient.RequestHook
import pw.binom.io.httpClient.protocol.ProtocolSelectorBySchema
import pw.binom.io.httpClient.protocol.v11.Http11ConnectFactory2
import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.io.socket.UnknownHostException
import pw.binom.io.use
import pw.binom.io.useAsync
import pw.binom.logger.Logger
import pw.binom.logger.debug
import pw.binom.logger.info
import pw.binom.network.NetworkManager
import pw.binom.proxy.ChannelBridge
import pw.binom.proxy.io.copyTo
import pw.binom.proxy.node.ClientService
import pw.binom.proxy.node.ProxedFactory
import pw.binom.proxy.node.RuntimeClientProperties
import pw.binom.proxy.node.SingleProtocolSelector
import pw.binom.proxy.node.exceptions.ClientMissingException
import pw.binom.strong.inject

class ProxyHandler : HttpHandler {
    private val clientService by inject<ClientService>()
    private val networkManager by inject<NetworkManager>()
    private val runtimeProperties by inject<RuntimeClientProperties>()
    private val logger by Logger.ofThisOrGlobal

    override suspend fun handle(exchange: HttpServerExchange) {
        when (exchange.requestMethod) {
            "CONNECT" -> tcp(exchange)
            else -> httpRequest(exchange)
        }
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
                ProxedFactory(protocolSelector = baseProtocolSelector, channelProvider = { url ->
                    clientService.connectTo(
                        host = url.host,
                        port = url.port ?: 80
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
        val req =
            try {
                httpClient.startConnect(
                    method = exchange.requestMethod,
                    uri = exchange.requestURI.toURL(),
                    headers = exchange.requestHeaders,
                    requestLength = OutputLength.None
                )
            } catch (e: ClientMissingException) {
                exchange.startResponse(503)
                return
            }
        if (exchange.requestHeaders.bodyExist) {
            logger.info("Copping http->ws")
            req.startWriteBinary().useAsync { output ->
                exchange.input.copyTo(output, bufferSize = runtimeProperties.bufferSize) {
                    logger.debug("http->ws $it")
                }
                output.flush()
            }
            logger.info("Request data sent!")
        }
        val resp = req.flush()
        exchange.startResponse(
            statusCode = resp.responseCode,
            headers = resp.inputHeaders + headersOf(Headers.PROXY_CONNECTION to Headers.KEEP_ALIVE)
        )
        if (resp.inputHeaders.bodyExist) {
            logger.info("Copping ws->http")
            resp.readBinary().useAsync { input ->
                logger.info("Input type: $input. available: ${input.available}")
                exchange.output.useAsync { output ->
                    input.copyTo(output, bufferSize = runtimeProperties.bufferSize) {
                        logger.debug("ws->http $it")
                    }
                }
            }
            logger.info("Response data sent!")
        }
        logger.info("Response finished!")
    }

    private suspend fun tcp(exchange: HttpServerExchange) {
        logger.info("User connected")
        val items = exchange.requestURI.toString().split(':', limit = 2)
        val host = items[0]
        val port = items[1].toInt()
        logger.info("Address: $host:$port")

        val connectionInfo =
            try {
                clientService.connectTo(
                    host = host,
                    port = port
                )
            } catch (e: UnknownHostException) {
                exchange.startResponse(404)
                return
            } catch (e: ClientMissingException) {
                exchange.startResponse(503)
                return
            }
        logger.info("Connected!")
        val input = exchange.input
        exchange.startResponse(200, emptyHeaders())
        val output = exchange.output
        logger.info("Try init connect on remote client!")
        val localChannel =
            AsyncChannel.create(
                input = input,
                output = output
            )
        val bridge =
            ChannelBridge.create(
                local = localChannel,
                remote = connectionInfo.second,
                bufferSize = runtimeProperties.bufferSize,
                logger = Logger.getLogger("Node Transport #${connectionInfo.first}"),
                localName = "node",
                id = connectionInfo.first,
                scope = networkManager
            )
        bridge.useAsync {
            it.join()
        }
//        val reversJob = GlobalScope.launch(coroutineContext) {
//            while (true) {
//                connectionInfo.copyTo(output, bufferSize = runtimeProperties.bufferSize) {
//                    logger.debug("server<-remote $it")
//                }
//            }
//        }
//        try {
//            while (true) {
//                input.copyTo(connectionInfo, bufferSize = runtimeProperties.bufferSize) {
//                    logger.debug("server->remote $it")
//                }
//            }
//        } catch (e: SocketClosedException) {
//            // Do nothing
//        } catch (e: Throwable) {
//            logger.warn(text = "Error on passing data from input to output", exception = e)
//        } finally {
//            logger.info("request finished!!!")
//            reversJob.cancel(kotlinx.coroutines.CancellationException("Can't copy tcp->client"))
//            connectionInfo.asyncCloseAnyway()
//        }
    }
}
