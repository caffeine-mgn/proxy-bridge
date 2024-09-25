package pw.binom.proxy.gateway

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.properties.Properties
import pw.binom.*
import pw.binom.atomic.AtomicBoolean
import pw.binom.date.DateTime
import pw.binom.date.format.toDatePattern
import pw.binom.io.ByteBufferFactory
import pw.binom.io.file.File
import pw.binom.io.file.LocalFileSystem
import pw.binom.io.file.workDirectoryFile
import pw.binom.io.http.BasicAuth
import pw.binom.io.http.BearerAuth
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.HttpProxyConfig
import pw.binom.io.httpClient.create
import pw.binom.io.socket.*
import pw.binom.io.use
import pw.binom.network.*
import pw.binom.pool.GenericObjectPool
import pw.binom.process.exitProcess
import pw.binom.proxy.GLog
import pw.binom.proxy.gateway.properties.RuntimeProperties
import pw.binom.proxy.gateway.services.ChannelService
import pw.binom.proxy.gateway.services.GatewayControlService
import pw.binom.signal.Signal
import pw.binom.strong.ServiceProvider
import pw.binom.strong.Strong
import pw.binom.strong.bean
import pw.binom.strong.inject
import pw.binom.thread.Thread
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

fun ServiceProvider<NetworkManager>.asInstance() =
    object : NetworkManager {
        override fun attach(channel: MulticastUdpSocket): MulticastUdpConnection =
            service.attach(channel = channel)

        override fun attach(
            channel: TcpClientSocket,
            mode: ListenFlags,
        ): TcpConnection = service.attach(channel = channel, mode = mode)

        override fun attach(channel: TcpNetServerSocket): TcpServerConnection = service.attach(channel)

        override fun attach(channel: UdpNetSocket): UdpConnection = service.attach(channel)

        override fun <R> fold(
            initial: R,
            operation: (R, CoroutineContext.Element) -> R,
        ): R = service.fold(initial = initial, operation = operation)

        override fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? = service.get(key)

        override fun minusKey(key: CoroutineContext.Key<*>): CoroutineContext = service.minusKey(key)

        override fun wakeup() {
            service.wakeup()
        }
    }

suspend fun startProxyClient(
    properties: RuntimeProperties,
    networkManager: NetworkManager,
): Strong {
    val baseConfig =
        Strong.config {
            it.bean { networkManager }
            it.bean {
                LocalFileSystem(
                    root = Environment.workDirectoryFile,
                    byteBufferPool = GenericObjectPool(factory = ByteBufferFactory(DEFAULT_BUFFER_SIZE))
                )
            }
            it.bean {
                val proxyConfig =
                    properties.proxy?.let { proxyConfig ->
                        HttpProxyConfig(
                            address = proxyConfig.address,
                            auth =
                            proxyConfig.auth?.let { auth ->
                                when {
                                    auth.basicAuth != null ->
                                        BasicAuth(
                                            login = auth.basicAuth.user,
                                            password = auth.basicAuth.password
                                        )

                                    auth.bearerAuth != null -> BearerAuth(token = auth.bearerAuth.token)
                                    else -> null
                                }
                            }
                        )
                    }
                val nm = inject<NetworkManager>()
                HttpClient.create(networkDispatcher = nm.asInstance(), proxy = proxyConfig)
            }
//            it.bean { ConnectionFactory(inject(), inject()) }
//            it.bean { ClientControlService() }
            it.bean { ChannelService() }
//            it.bean { TransportService() }
            it.bean { FileService() }
            it.bean { properties }
            it.bean { BinomMetrics }
            it.bean { GatewayControlService() }
        }
    return Strong.create(baseConfig)
}

val closed = AtomicBoolean(false)

fun main(args: Array<String>) {
    Thread {
        Thread.sleep(1.hours)
        println("Goodbay. time to die")
        InternalLog.info(file = "main") { "Goodbay. time to die" }
        closed.setValue(true)
        exitProcess(0)
    }.start()
    Thread {
        while (!closed.getValue()) {
            Thread.sleep(5.minutes)
            InternalLog.info(file = "main") { "Making GC" }
            System.gc()
        }
    }.start()
    val date = "yyyy-MM-dd-HH-mm".toDatePattern().toString(DateTime.now, DateTime.systemZoneOffset)
    InternalLog.default = GLog(File(Environment.currentExecutionPath).parent!!.relative("$date.glog"))
    val params =
        args.filter { it.startsWith("-D") }.associate {
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
