package pw.binom.proxy.client

import pw.binom.DEFAULT_BUFFER_SIZE
import pw.binom.io.*
import pw.binom.io.http.*
import pw.binom.io.httpClient.protocol.v11.Http11ConnectFactory2
import pw.binom.io.socket.InetNetworkAddress
import pw.binom.io.socket.NetworkAddress
import pw.binom.network.NetworkManager
import pw.binom.network.tcpConnect

suspend fun NetworkManager.tcpConnectViaHttpProxy(
    proxy: InetNetworkAddress,
    address: NetworkAddress,
    readBufferSize: Int = DEFAULT_BUFFER_SIZE,
    auth: HttpAuth? = null,
    headers: Headers = emptyHeaders(),
) = tcpConnect(address = proxy)
    .tcpConnectViaHttpProxy(
        address = address,
        readBufferSize = readBufferSize,
        auth = auth,
        headers = headers,
    )

suspend fun AsyncChannel.tcpConnectViaHttpProxy(
    address: NetworkAddress,
    readBufferSize: Int = DEFAULT_BUFFER_SIZE,
    auth: HttpAuth? = null,
    headers: Headers = emptyHeaders(),
): AsyncChannel {
    val request = "${address.host}:${address.port}"
    val headersForSend = HashHeaders2()
    headersForSend[Headers.HOST] = request
    if (auth != null) {
        headersForSend[Headers.PROXY_AUTHORIZATION] = auth.headerValue
    }
    headersForSend.add(headers)

    bufferedAsciiWriter(closeParent = false).use { writer ->
        Http11ConnectFactory2.sendRequest(
            output = writer,
            method = "CONNECT",
            request = request,
            headers = headersOf(Headers.HOST to request),
        )
    }
    val reader = bufferedAsciiReader(bufferSize = readBufferSize)
    val resp = try {
        Http11ConnectFactory2.readResponse(reader)
    } catch (e: Throwable) {
        reader.asyncCloseAnyway()
        throw e
    }
    if (resp.responseCode != 200) {
        reader.asyncClose()
        asyncClose()
        throw IOException("Can't connect via http proxy: invalid response ${resp.responseCode}")
    }
    return AsyncChannel.create(
        input = reader,
        output = this,
    ) {
        this.asyncClose()
    }
}
