package pw.binom.proxy

import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import pw.binom.io.AsyncCloseable
import pw.binom.io.Closeable
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.HttpProxyConfig
import pw.binom.io.httpClient.create
import pw.binom.io.socket.InetNetworkAddress
import pw.binom.io.socket.NetworkAddress
import pw.binom.io.use
import pw.binom.logger.Logger
import pw.binom.logger.WARNING
import pw.binom.network.MultiFixedSizeThreadNetworkDispatcher
import pw.binom.network.NetworkManager
import pw.binom.network.TcpServerConnection
import pw.binom.proxy.client.RuntimeProperties
import pw.binom.proxy.client.startProxyClient
import pw.binom.proxy.node.startProxyNode
import pw.binom.strong.Strong
import pw.binom.url.toURL
import kotlin.time.Duration.Companion.seconds
import pw.binom.proxy.client.RuntimeProperties as ClientRuntimeProperties
import pw.binom.proxy.node.RuntimeClientProperties as NodeRuntimeProperties

suspend fun HttpClient.checkIsOk() {
    connect(method = "GET", uri = "https://www.google.com/".toURL())
        .getResponse()
        .readText {
            it.readText()
        }
}

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

class Ports {
    val externalPort = TcpServerConnection.randomPort()
    val internalPort = TcpServerConnection.randomPort()

    val nodeConfig
        get() =
            NodeRuntimeProperties(
                externalBinds = listOf(InetNetworkAddress.create(host = "127.0.0.1", port = externalPort)),
                internalBinds = listOf(InetNetworkAddress.create(host = "127.0.0.1", port = internalPort)),
                bufferSize = 1024 * 1024
            )

    fun clientConfig(transportType: ClientRuntimeProperties.TransportType) =
        ClientRuntimeProperties(
            url = "http://localhost:$externalPort".toURL(),
            transportType = transportType,
            proxy =
                ClientRuntimeProperties.Proxy(
                    address = NetworkAddress.create(host = "127.0.0.1", port = 8888)
                ),
            bufferSize = 1024 * 1024
        )

    fun createHttpClient(nd: NetworkManager) =
        HttpClient.create(
            networkDispatcher = nd,
            proxy =
                HttpProxyConfig(
                    address =
                        NetworkAddress.create(
                            host = "127.0.0.1",
                            port = internalPort
                        )
                ),
            bufferSize = 1024 * 1024 * 10
        )

    suspend fun createNode(
        nd: NetworkManager,
        transportType: ClientRuntimeProperties.TransportType,
        config: (RuntimeProperties) -> RuntimeProperties = { it },
    ) = startProxyClient(properties = config(clientConfig(transportType)), networkManager = nd)

    suspend fun createServer(
        nd: NetworkManager,
        config: (NodeRuntimeProperties) -> NodeRuntimeProperties = { it },
    ) = startProxyNode(properties = config(nodeConfig), networkManager = nd)

    suspend fun instance(
        transportType: ClientRuntimeProperties.TransportType,
        nd: NetworkManager,
    ): Instance {
        val server = createServer(nd)
        delay(1.seconds)
        val node = createNode(nd = nd, transportType = transportType)
        val client = createHttpClient(nd)
        return Instance(
            server = server,
            node = node,
            client = client,
            nd = null,
            networkManager = nd
        )
    }

    suspend fun instance(transportType: ClientRuntimeProperties.TransportType): Instance {
        val nd = prepareNetworkDispatcher()
        return withContext(nd) {
            val server = createServer(nd)
            delay(1.seconds)

            val node = createNode(nd = nd, transportType = transportType)
            val client = createHttpClient(nd)
            Instance(
                server = server,
                node = node,
                client = client,
                nd = nd,
                networkManager = nd
            )
        }
    }

    fun prepareNetworkDispatcher() = MultiFixedSizeThreadNetworkDispatcher(2)
}

class Instance(
    val server: Strong,
    val node: Strong,
    val client: HttpClient,
    val nd: Closeable?,
    val networkManager: NetworkManager,
) :
    AsyncCloseable {
    override suspend fun asyncClose() {
        client.closeAnyway()
        if (!node.isDestroying && !node.isDestroyed) {
            node.destroy()
            node.awaitDestroy()
        }
        println("Closing NETWORKMANAGER")
        nd?.close()
    }
}

suspend fun prepareNetwork(
    transportType: ClientRuntimeProperties.TransportType,
    func: suspend (HttpClient) -> Unit,
) {
    Logger.getLogger("Strong.Starter").level = Logger.WARNING
    val ports = Ports()
    ports.instance(transportType = transportType).use {
        withContext(it.networkManager) {
            func(it.client)
        }
    }
    return

    println("Node external: ${ports.externalPort}")
    println("Node internal: ${ports.internalPort}")
    MultiFixedSizeThreadNetworkDispatcher(2).use { nd ->
        val server = ports.createServer(nd)
        delay(1.seconds)
        try {
            val client = ports.createNode(nd = nd, transportType = transportType)
            try {
                ports.createHttpClient(nd).use { httpClient ->
                    func(httpClient)
                }
            } finally {
                client.destroy()
            }
        } finally {
            server.destroy()
        }
    }
}
