package pw.binom.http

import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headers
import io.ktor.network.selector.*
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.util.appendAll
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readLineStrict
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Реализация HTTP PROXY
 */
class HttpProxy(
    port: Int,
    private val selector: SelectorManager,
    private val onConnect: ConnectProcessing = ConnectProcessing { _, _, _ -> },
    private val onHttp: HttpProcessing = HttpProcessing { _, _, _, _ -> },
) : AutoCloseable {

    interface ProxyingRawContext {
        suspend fun ok(): Pair<ByteReadChannel, ByteWriteChannel>
        suspend fun ioError()
        suspend fun timeout()
        suspend fun notAvailable()
    }

    interface ProxyingHttpContext {
        suspend fun readRequest(): ByteReadChannel
        suspend fun sendResponse(status: HttpStatusCode, headers: Headers): ByteWriteChannel
    }

    fun interface HttpProcessing {
        suspend fun request(uri: Url, method: HttpMethod, headers: Headers, context: ProxyingHttpContext)
    }

    fun interface ConnectProcessing {
        suspend fun connect(host: String, port: Int, context: ProxyingRawContext)
    }

    private val serverJob = selector.launch {
        aSocket(selector).tcp().bind(hostname = "0.0.0.0", port = port).use { server ->
            while (isActive) {
                val client = server.accept()
                launch {
                    client.use { client ->
                        clientProcessing(client)
                    }
                }
            }
        }
    }

    private suspend fun ByteWriteChannel.httpResponse(
        status: HttpStatusCode,
        headers: HeadersBuilder.() -> Unit = { contentLength(0) }
    ) {
        val builder = HeadersBuilder()
        headers(builder)
        httpResponse(status = status, headers = builder.build())
    }

    private suspend fun ByteWriteChannel.httpResponse(
        status: HttpStatusCode,
        headers: Headers,
    ) {
        writeStringUtf8("HTTP/1.1 ${status.value} ${status.description}\r\n")
        headers.names().forEach { key ->
            headers.getAll(key)?.forEach { value ->
                writeStringUtf8("$key: $value\r\n")
            }
        }
        writeStringUtf8("\r\n")
        flush()
    }

    private suspend fun clientProcessing(client: Socket) {
        val read = client.openReadChannel()
        val write = client.openWriteChannel(autoFlush = false)
        val request = read.readLineStrict()
        if (request == null) {
            write.httpResponse(HttpStatusCode.BadRequest)
            return
        }

        val headersBuilder = HeadersBuilder()
        while (true) {
            val line = read.readLineStrict() ?: break
            if (line.isEmpty()) break
            val parts = line.split(": ", limit = 2)
            if (parts.size == 2) headersBuilder.append(parts[0], parts[1])
        }
        headersBuilder.remove("Proxy-Connection")
        val headers = headersBuilder.build()

        val items = request.split(" ")
        val method = items[0]
        val path = items[1]

        if (method == "CONNECT") {
            val hostItems = path.split(':', limit = 2)
            val host = hostItems[0]
            val port = hostItems.getOrNull(1)?.toIntOrNull() ?: 443

            var called = false
            onConnect.connect(
                host = host,
                port = port,
                context = object : ProxyingRawContext {
                    override suspend fun ok(): Pair<ByteReadChannel, ByteWriteChannel> {
                        check(!called)
                        called = true
                        write.httpResponse(HttpStatusCode.ConnectionEstablished) {
                            contentLength(0)
                            append("Proxy-Connection", "keep-alive")
                        }
                        return read to write
                    }

                    override suspend fun ioError() {
                        check(!called)
                        called = true
                        write.httpResponse(HttpStatusCode.BadGateway)
                    }

                    override suspend fun timeout() {
                        check(!called)
                        called = true
                        write.httpResponse(HttpStatusCode.GatewayTimeout)
                    }

                    override suspend fun notAvailable() {
                        check(!called)
                        called = true
                        write.httpResponse(HttpStatusCode.ServiceUnavailable)
                    }
                }
            )
            if (!called) {
                write.httpResponse(HttpStatusCode.NotFound)
            } else {
                write.flush()
            }
            return
        }
        var called1 = false
        var called2 = false
        var outStream: ChunkedByteWriteChannel? = null
        onHttp.request(
            uri = Url(path),
            method = HttpMethod.parse(method),
            headers = headers,
            context = object : ProxyingHttpContext {
                override suspend fun readRequest(): ByteReadChannel {
                    check(!called1)
                    check(!called2)
                    called1 = true
                    return read
                }

                override suspend fun sendResponse(
                    status: HttpStatusCode,
                    headers: Headers
                ): ByteWriteChannel {
                    check(!called2)
                    called2 = true
                    val e = HeadersBuilder()
                    e.appendAll(headers)
                    e.remove("Transfer-Encoding")
                    e.remove("Content-Length")
                    e.remove("Connection")
                    e.append("Transfer-Encoding", "chunked")
                    e.append("Connection", "closed")
                    write.httpResponse(status = status, headers = e.build())
                    val c = ChunkedByteWriteChannel(write)
                    outStream = c
                    return c
                }
            }
        )
        if (!called2) {
            write.httpResponse(HttpStatusCode.NotFound)
        } else {
            outStream?.flushAndClose()
            write.flush()
        }
    }

    suspend fun join() {
        serverJob.join()
    }

    override fun close() {
        serverJob.cancel()
    }
}
