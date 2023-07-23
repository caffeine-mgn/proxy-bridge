package pw.binom.proxy.client

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.properties.Properties
import pw.binom.*
import pw.binom.io.ByteBufferFactory
import pw.binom.io.file.LocalFileSystem
import pw.binom.io.file.workDirectoryFile
import pw.binom.io.http.BasicAuth
import pw.binom.io.http.BearerAuth
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.HttpProxyConfig
import pw.binom.io.httpClient.create
import pw.binom.io.socket.TcpClientSocket
import pw.binom.io.socket.TcpNetServerSocket
import pw.binom.io.socket.UdpNetSocket
import pw.binom.io.use
import pw.binom.network.*
import pw.binom.pool.GenericObjectPool
import pw.binom.signal.Signal
import pw.binom.strong.ServiceProvider
import pw.binom.strong.Strong
import pw.binom.strong.bean
import pw.binom.strong.inject
import kotlin.coroutines.CoroutineContext

fun ServiceProvider<NetworkManager>.asInstance() = object : NetworkManager {
    override fun attach(channel: TcpClientSocket, mode: Int): TcpConnection =
        service.attach(channel = channel, mode = mode)

    override fun attach(channel: TcpNetServerSocket): TcpServerConnection = service.attach(channel)

    override fun attach(channel: UdpNetSocket): UdpConnection = service.attach(channel)

    override fun <R> fold(initial: R, operation: (R, CoroutineContext.Element) -> R): R =
        service.fold(initial = initial, operation = operation)

    override fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? = service.get(key)

    override fun minusKey(key: CoroutineContext.Key<*>): CoroutineContext = service.minusKey(key)

    override fun wakeup() {
        service.wakeup()
    }
}

suspend fun startProxyClient(properties: RuntimeProperties, networkManager: NetworkManager): Strong {
    val baseConfig = Strong.config {
        it.bean { networkManager }
        it.bean {
            LocalFileSystem(
                root = Environment.workDirectoryFile,
                byteBufferPool = GenericObjectPool(factory = ByteBufferFactory(DEFAULT_BUFFER_SIZE))
            )
        }
        it.bean {
            val proxyConfig = properties.proxy?.let { proxyConfig ->
                HttpProxyConfig(
                    address = proxyConfig.address,
                    auth = proxyConfig.auth?.let { auth ->
                        when {
                            auth.basicAuth != null -> BasicAuth(
                                login = auth.basicAuth.user,
                                password = auth.basicAuth.password,
                            )

                            auth.bearerAuth != null -> BearerAuth(token = auth.bearerAuth.token)
                            else -> null
                        }
                    },
                )
            }
            val nm = inject<NetworkManager>()
            HttpClient.create(networkDispatcher = nm.asInstance(), proxy = proxyConfig)
        }
        it.bean { ConnectionFactory(inject(), inject()) }
        it.bean { ClientControlService() }
        it.bean { TransportService() }
        it.bean { FileService() }
        it.bean { properties }
    }
    return Strong.create(baseConfig)
}

fun main(args: Array<String>) {
    val params = args.filter { it.startsWith("-D") }.associate {
        val items = it.removePrefix("-D").split('=', limit = 2)
        items[0] to items[1]
    }
    val properties = Properties.decodeFromStringMap(RuntimeProperties.serializer(), params)
    runBlocking {
        MultiFixedSizeThreadNetworkDispatcher(Environment.availableProcessors).use { networkManager ->
            val strong = startProxyClient(properties = properties, networkManager = networkManager)
            val mainCoroutineContext = coroutineContext
            Signal.handler {
                if (it.isInterrupted) {
                    if (!strong.isDestroying && !strong.isDestroyed) {
                        GlobalScope.launch(mainCoroutineContext) {
                            println("destroying...")
                            strong.destroy()
                            println("destroyed!!!")
                        }
                    }
                }
            }
            strong.awaitDestroy()
            println("Main finished!")
        }
    }
}
