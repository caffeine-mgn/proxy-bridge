package pw.binom.proxy.gateway

import pw.binom.io.AsyncChannel
import pw.binom.io.http.Encoding
import pw.binom.io.http.Headers
import pw.binom.io.http.emptyHeaders
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.addHeader
import pw.binom.io.httpClient.connectTcp
import pw.binom.io.httpClient.connectWebSocket
import pw.binom.proxy.Urls
import pw.binom.proxy.gateway.properties.RuntimeProperties
import pw.binom.proxy.io.AsyncInputViaWebSocketMessage
import pw.binom.strong.ServiceProvider

class ConnectionFactory(
    httpClient: ServiceProvider<HttpClient>,
    runtimeProperties: ServiceProvider<RuntimeProperties>
) {
    private val httpClient by httpClient
    private val runtimeProperties by runtimeProperties
    private suspend fun createWsConnection(id: Int): AsyncChannel {
        val transportUrl = runtimeProperties.url.addPath(Urls.TRANSPORT_WS.toPath { id.toString() })
        val transportConnection = httpClient.connectWebSocket(
            uri = transportUrl,
            masking = runtimeProperties.wsMasking,
        ).start()
        return AsyncInputViaWebSocketMessage(transportConnection)
    }

    private suspend fun createViaTcpConnection(id: Int): AsyncChannel =
        httpClient.connectTcp(
            uri = runtimeProperties.url.addPath(Urls.TRANSPORT_TCP.toPath { id.toString() }),
            headers = emptyHeaders(),
        ).start()

    private suspend fun createViaHttpPooling(id: Int): AsyncChannel {
        val transportUrlRead =
            runtimeProperties.url.addPath(Urls.TRANSPORT_LONG_POOLING_NODE_WRITE.toPath { id.toString() })
        val transportUrlWrite =
            runtimeProperties.url.addPath(Urls.TRANSPORT_LONG_POOLING_CLIENT_WRITE.toPath { id.toString() })
        val readRequest = httpClient.connect(
            uri = transportUrlRead,
            method = "POST",
        )
        val resp = readRequest.getResponse()
        val writeRequest = httpClient.connect(
            uri = transportUrlWrite,
            method = "POST",
        ).addHeader(Headers.TRANSFER_ENCODING, Encoding.CHUNKED)

        return AsyncChannel.create(
            input = resp.readBinary(),
            output = writeRequest.writeBinary(),
        )
    }

    suspend fun connect(id: Int): AsyncChannel =
        when (runtimeProperties.transportType) {
            RuntimeProperties.TransportType.TCP_OVER_HTTP -> createViaTcpConnection(id)
            RuntimeProperties.TransportType.WS -> createWsConnection(id)
            RuntimeProperties.TransportType.HTTP_POOLING -> createViaHttpPooling(id)
        }
}
