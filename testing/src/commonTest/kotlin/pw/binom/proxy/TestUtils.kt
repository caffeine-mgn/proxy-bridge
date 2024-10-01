package pw.binom.proxy

import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import pw.binom.*
import pw.binom.io.AsyncCloseable
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.HttpProxyConfig
import pw.binom.io.httpClient.create
import pw.binom.io.socket.DomainSocketAddress
import pw.binom.io.socket.InetSocketAddress
import pw.binom.io.useAsync
import pw.binom.logger.Logger
import pw.binom.logger.WARNING
import pw.binom.network.MultiFixedSizeThreadNetworkDispatcher
import pw.binom.network.NetworkManager
import pw.binom.gateway.properties.GatewayRuntimeProperties
import pw.binom.gateway.startProxyClient
import pw.binom.proxy.services.ExternalWebServerService
import pw.binom.proxy.services.InternalWebServerService
import pw.binom.proxy.properties.ProxyProperties
import pw.binom.proxy.services.ServerControlService
import pw.binom.strong.Strong
import pw.binom.strong.inject
import pw.binom.url.toURL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class Server(
    val context: Strong,
    val networkManager: NetworkManager,
) : AsyncCloseable {
    val externalPort
        get() = context.inject<ExternalWebServerService>().service.port
    val internalPort
        get() = context.inject<InternalWebServerService>().service.port

    val controlService by context.inject<ServerControlService>()

    override suspend fun asyncClose() {
        context.destroy()
        context.awaitDestroy()
    }

    fun createHttpClient(bufferSize: Int = DEFAULT_BUFFER_SIZE, networkManager: NetworkManager = this.networkManager) =
        HttpClient.create(
            networkDispatcher = networkManager,
            proxy =
            HttpProxyConfig(
                address =
                DomainSocketAddress(
                    host = "127.0.0.1",
                    port = internalPort
                )
            ),
            bufferSize = bufferSize,
        )
}

class Gateway(val context: Strong) : AsyncCloseable {
    override suspend fun asyncClose() {
        context.destroy()
        context.awaitDestroy()
    }
}

class Context(
    val server: Server,
    val gateway: Gateway,
    val networkManager: MultiFixedSizeThreadNetworkDispatcher,
) : AsyncCloseable {
    val client by lazy { server.createHttpClient() }
    override suspend fun asyncClose() {
        client.close()
        gateway.asyncClose()
        server.asyncClose()
        networkManager.close()
    }

    suspend fun wait(timeout: Duration = 5.seconds, func: suspend () -> Boolean) {
        val now = TimeSource.Monotonic.markNow()
        while (!func()) {
            if (now.elapsedNow() > timeout) {
                throw RuntimeException("Timeout")
            }
            delay(50)
        }
    }

    object TestUtils {
        suspend fun <T> context(func: suspend Context.() -> T): T =
            context().useAsync {
                func(it)
            }

        suspend fun context(): Context {
            Logger.getLogger("Strong.Starter").level = Logger.WARNING
            val networkManager1 = MultiFixedSizeThreadNetworkDispatcher(5)
            val networkManager2 = MultiFixedSizeThreadNetworkDispatcher(5)
            val networkManager3 = MultiFixedSizeThreadNetworkDispatcher(5)
            return withContext(networkManager1) {
                val server = try {
                    startServer(networkManager2)
                } catch (e: Throwable) {
                    networkManager1.close()
                    networkManager2.close()
                    throw e
                }
                val gateway = try {
                    createGateway(
                        networkManager = networkManager3,
                        server = server
                    )
                } catch (e: Throwable) {
                    networkManager1.close()
                    networkManager2.close()
                    networkManager3.close()
                    server.asyncClose()
                    throw e
                }
                Context(
                    server = server,
                    gateway = gateway,
                    networkManager = networkManager1,
                )
            }
        }

        suspend fun startServer(
            networkManager: NetworkManager,
            config: (ProxyProperties) -> ProxyProperties = { it },
        ): Server {
            val conf = ProxyProperties(
                internalBinds = listOf(InetSocketAddress.resolve("127.0.0.1", 0)),
                externalBinds = listOf(InetSocketAddress.resolve("127.0.0.1", 0)),
            )
            val context = startProxyNode(
                properties = config(conf),
                networkManager = networkManager,
            )
            return Server(context = context, networkManager = networkManager)
        }

        suspend fun createGateway(
            networkManager: NetworkManager,
            server: Server,
            properties: (GatewayRuntimeProperties) -> GatewayRuntimeProperties = { it },
        ): Gateway {
            val config = GatewayRuntimeProperties(url = "http://127.0.0.1:${server.externalPort}".toURL())
            return createGateway(
                networkManager = networkManager,
                properties = properties(config)
            )
        }

        suspend fun createGateway(
            networkManager: NetworkManager,
            properties: GatewayRuntimeProperties,
        ): Gateway {
            val context = withContext(networkManager) {
                startProxyClient(
                    networkManager = networkManager,
                    properties = properties,
                )
            }
            return Gateway(context)
        }
    }
}

suspend fun HttpClient.getText(method: String = "GET", url: String) =
    connect(method = method, uri = url.toURL())
        .getResponse()
        .readText { it.readText() }
