package pw.binom.proxy.node

import pw.binom.io.AsyncChannel
import pw.binom.io.http.Headers
import pw.binom.io.httpClient.HttpRequestBody
import pw.binom.io.httpClient.protocol.ConnectionPoll
import pw.binom.io.httpClient.protocol.HttpConnect
import pw.binom.io.httpClient.protocol.ProtocolSelector
import pw.binom.url.URL
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
                pool = { _, _ -> pool.recycle("${url.host}:${url.port}", this) },
                method = method,
                url = url,
                headers = headers,
        )
    }
}
