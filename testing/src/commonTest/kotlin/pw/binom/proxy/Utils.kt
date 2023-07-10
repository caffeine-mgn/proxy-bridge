package pw.binom.proxy

import kotlinx.coroutines.delay
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.HttpProxyConfig
import pw.binom.io.httpClient.create
import pw.binom.io.socket.InetNetworkAddress
import pw.binom.io.socket.NetworkAddress
import pw.binom.io.use
import pw.binom.logger.Logger
import pw.binom.logger.WARNING
import pw.binom.network.MultiFixedSizeThreadNetworkDispatcher
import pw.binom.network.TcpServerConnection
import pw.binom.proxy.client.startProxyClient
import pw.binom.proxy.node.startProxyNode
import pw.binom.url.toURL
import kotlin.time.Duration.Companion.seconds
import pw.binom.proxy.client.RuntimeProperties as ClientRuntimeProperties
import pw.binom.proxy.node.RuntimeProperties as NodeRuntimeProperties

suspend fun prepareNetwork(func: suspend (HttpClient) -> Unit) {
    ClientRuntimeProperties.TransportType.values().forEach {
        try {
            println("----====TESTING $it====----")
            prepareNetwork(transportType = it, func)
        } catch (e: Throwable) {
            throw RuntimeException("Fail on transport $it", e)
        } finally {
            println("----====TESTING $it====----")
        }
    }
}

suspend fun prepareNetwork(transportType: ClientRuntimeProperties.TransportType, func: suspend (HttpClient) -> Unit) {
    Logger.getLogger("Strong.Starter").level = Logger.WARNING
    val externalPort = TcpServerConnection.randomPort()
    val internalPort = TcpServerConnection.randomPort()
    val clientConfig = ClientRuntimeProperties(
        url = "http://localhost:$externalPort".toURL(),
        transportType = transportType,
        proxy = ClientRuntimeProperties.Proxy(
            address = NetworkAddress.create(host = "127.0.0.1", port = 8888),
        )
    )
    val nodeConfig = NodeRuntimeProperties(
        externalBinds = listOf(InetNetworkAddress.create(host = "0.0.0.0", port = externalPort)),
        internalBinds = listOf(InetNetworkAddress.create(host = "0.0.0.0", port = internalPort)),
    )

    println("Node external: $externalPort")
    println("Node internal: $internalPort")

    val server = startProxyNode(nodeConfig)
    delay(2.seconds)
    try {
        val client = startProxyClient(clientConfig)
        try {
            MultiFixedSizeThreadNetworkDispatcher(2).use { nd ->
                HttpClient.create(
                    networkDispatcher = nd,
                    proxy = HttpProxyConfig(
                        address = NetworkAddress.create(
                            host = "127.0.0.1",
                            port = internalPort
                        )
                    )
                ).use { httpClient ->
                    func(httpClient)
                }
            }
        } finally {
            client.destroy()
        }
    } finally {
        server.destroy()
    }
}
