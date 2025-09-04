package pw.binom.transport.controllers

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import pw.binom.DEFAULT_BUFFER_SIZE
import pw.binom.atomic.AtomicLong
import pw.binom.copyTo
import pw.binom.http.client.Http11ClientExchange
import pw.binom.http.client.HttpClientRunnable
import pw.binom.http.client.factory.Http11ConnectionFactory
import pw.binom.http.client.factory.Https11ConnectionFactory
import pw.binom.io.AsyncChannel
import pw.binom.io.http.HashHeaders
import pw.binom.io.http.Headers
import pw.binom.io.http.headersOf
import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.io.useAsync
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.warn
import pw.binom.transport.VirtualManager
import pw.binom.transport.io.Cooper
import pw.binom.transport.io.HttpsConverterChannel
import pw.binom.transport.io.VirtualSocketFactory
import pw.binom.transport.services.TcpBridgeService
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

class ProxyHandler(
    val manager: VirtualManager,
    val httpsDomains: Set<String> = setOf("nexus.isb", "jira.otpbank.ru", "bitbucket.isb"),
) : HttpHandler {

    private val logger by Logger.ofThisOrGlobal
    private val connectCount = AtomicLong(0)

    private val httpClient2 by lazy {
        HttpClientRunnable(
            factory = Https11ConnectionFactory(
                fallback = Http11ConnectionFactory(),
            ),
            source = VirtualSocketFactory(manager)
        )
    }

    override suspend fun handle(exchange: HttpServerExchange) {
        when (exchange.requestMethod) {
            "CONNECT" -> tcp(exchange)
            else -> httpRequest(exchange)
        }
    }

    private suspend fun tcp(exchange: HttpServerExchange) {
        logger.info("User connected")
        val items = exchange.requestURI.toString().split(':', limit = 2)
        val host = items[0]
        val port = items[1].toInt()
        val channel =
            try {
                TcpBridgeService.connect(manager = manager, host = host, port = port)
            } catch (e: TimeoutCancellationException) {
                logger.info("Timeout connect to $host:$port")
                exchange.startResponse(504, headersOf(Headers.CONNECTION to Headers.CLOSE))
                return
            } catch (e: Throwable) {
                logger.warn(text = "Can't connect to $host:$port", exception = e)
                e.printStackTrace()
                exchange.startResponse(500, headersOf(Headers.CONNECTION to Headers.CLOSE))
                return
            }
        logger.info("Channel connected! Try return code 200")
        exchange.startResponse(200, headersOf(Headers.CONNECTION to Headers.CLOSE))
        var incomeChannel = AsyncChannel.create(
            input = exchange.input,
            output = exchange.output
        )

        if (host in httpsDomains && port == 80) {
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
                Cooper.exchange(
                    first = incomeChannel,
                    second = channel,
                    context = coroutineContext,
                )
            }
            logger.info("Proxy finished!")
        } catch (e: Throwable) {
            throw e
        } finally {
            connectCount.dec()
        }
    }

    private suspend fun httpRequest(exchange: HttpServerExchange) {
        val req =
            withTimeoutOrNull(10.seconds) {
                val newHeaders = HashHeaders()
                newHeaders.addAll(exchange.requestHeaders.toSimpleHeaders())
                newHeaders[Headers.CONNECTION] = "Close"
                var remoteUrl = exchange.requestURI.toURL()
                val remotePort = remoteUrl.port ?: 80
                logger.info("URL: ${remoteUrl.host}:${remoteUrl.port}")
                if (remoteUrl.domain in httpsDomains && (remotePort == 80)) {
                    remoteUrl = remoteUrl.copy(schema = "https", port = 443)
                    logger.info("NEW URL: $remoteUrl")
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
            logger.info("Can't connect to remote http server: Timeout")
            exchange.startResponse(500)
            return
        }
        if (exchange.requestHeaders.bodyExist) {
            req.getOutput().useAsync { output ->
                exchange.input.copyTo(output, bufferSize = DEFAULT_BUFFER_SIZE)
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
                    input.copyTo(output, bufferSize = DEFAULT_BUFFER_SIZE)
                }
            }
        }
    }
}
