package pw.binom.proxy.handlers

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import pw.binom.DEFAULT_BUFFER_SIZE
import pw.binom.io.AsyncChannel
import pw.binom.io.ByteBuffer
import pw.binom.io.http.Encoding
import pw.binom.io.http.Headers
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.httpClient.*
import pw.binom.io.httpClient.protocol.*
import pw.binom.io.httpClient.protocol.v11.Http11ConnectFactory2
import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.io.use
import pw.binom.proxy.io.*
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.warn
import pw.binom.network.NetworkManager
import pw.binom.proxy.ClientService
import pw.binom.strong.inject
import pw.binom.url.URL
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
class ProxedHttpConnect(
    val channelProvider: suspend (url: URL) -> AsyncChannel,
    val protocolSelector: ProtocolSelector,
) : HttpConnect {

    private var created = TimeSource.Monotonic.markNow()
    override val age: Duration
        get() = created.elapsedNow()
    private var closed = false
    override val isAlive: Boolean
        get() = !closed && (connection == null || connection?.isAlive == true)
    private var connection: HttpConnect? = null

    override suspend fun asyncClose() {
        closed = true
        connection?.asyncCloseAnyway()
    }

    override suspend fun makePostRequest(
        pool: ConnectionPoll,
        method: String,
        url: URL,
        headers: Headers
    ): HttpRequestBody {
        var connection = connection
        if (connection == null) {
            connection = protocolSelector.select(url)
                .createConnect(channelProvider(url))
        }
        return connection.makePostRequest(
            pool = { _, _ -> },
            method = method,
            url = url,
            headers = headers,
        )
    }
}

class ProxedFactory(
    val protocolSelector: ProtocolSelector,
    val channelProvider: suspend (url: URL) -> AsyncChannel,
) : ConnectFactory2 {
    override fun createConnect(): HttpConnect =
        ProxedHttpConnect(
            channelProvider = channelProvider,
            protocolSelector = protocolSelector,
        )

}

class SingleProtocolSelector(val factory: ConnectFactory2) : ProtocolSelector {
    override fun find(url: URL) = factory

}

class ProxyHandler : HttpHandler {
    private val clientService by inject<ClientService>()
    private val networkManager by inject<NetworkManager>()
    private val logger by Logger.ofThisOrGlobal
    override suspend fun handle(exchange: HttpServerExchange) {
        when (exchange.requestMethod) {
            "CONNECT" -> tcp(exchange)
            else -> httpRequest(exchange)
        }
    }

    val httpClient by lazy {
        val baseProtocolSelector = ProtocolSelectorBySchema()
        val http = Http11ConnectFactory2(networkManager = networkManager)
        baseProtocolSelector.set(
            http,
            "http",
            "ws",
        )

        val pp = SingleProtocolSelector(ProxedFactory(
            protocolSelector = baseProtocolSelector,
            channelProvider = { url ->
                clientService.connectTo(
                    host = url.host,
                    port = url.port ?: 80,
                )
            }
        ))

        BaseHttpClient(
            useKeepAlive = true,
            protocolSelector = pp,
            requestHook = RequestHook.Default,
        )
    }

    private suspend fun httpRequest(exchange: HttpServerExchange) {
        val req = httpClient.startConnect(
            method = exchange.requestMethod,
            uri = exchange.requestURI.toURL(),
            headers = exchange.requestHeaders,
            requestLength = OutputLength.None,
        )
        if (exchange.requestHeaders.bodyExist) {
            logger.info("Copping http->ws")
            req.startWriteBinary().use { output ->
                exchange.input.copyTo(output) {
                    logger.info("http->ws $it")
                }
            }
            logger.info("Request data sent!")
        }
        val resp = req.flush()
        exchange.startResponse(resp.responseCode, headers = resp.headers)
        println("Response Headers:\n${resp.headers}")
        if (resp.headers.bodyExist) {
            logger.info("Copping ws->http")
            resp.readData().use { input ->
                logger.info("Input type: $input. available: ${input.available}")
                exchange.output.use { output ->
                    input.copyTo(output) {
                        logger.info("ws->http $it")
                    }
                }
            }
            logger.info("Response data sent!")
        }
        logger.info("Response finished!")
    }

    private suspend fun makeConnection(host: String, port: Int) {
    }

    private suspend fun tcp(exchange: HttpServerExchange) {
        logger.info("User connected")
        val items = exchange.requestURI.toString().split(':', limit = 2)
        val host = items[0]
        val port = items[1].toInt()
        logger.info("Address: $host:$port")
        val input = exchange.input
        exchange.startResponse(200)
        val output = exchange.output
        logger.info("Try init connect on remote client!")

        val connectionInfo = clientService.connectTo(
            host = host,
            port = port,
        )
        val reversJob = GlobalScope.launch(coroutineContext) {
            while (true) {
                connectionInfo.copyTo(output, bufferSize = DEFAULT_BUFFER_SIZE) {
                    logger.info("ws->tcp $it")
                }
            }
        }
        try {
            while (true) {
                input.copyTo(connectionInfo, bufferSize = DEFAULT_BUFFER_SIZE) {
                    logger.info("tcp->ws $it")
                }
            }
        } catch (e: Throwable) {
            logger.warn(text = "Error on passing data from input to output", exception = e)
            e.printStackTrace()
        } finally {
            logger.info("request finished!!!")
            reversJob.cancel(kotlinx.coroutines.CancellationException("Can't copy tcp->client"))
            connectionInfo.asyncCloseAnyway()
        }
    }
}
