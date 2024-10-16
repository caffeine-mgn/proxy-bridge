package pw.binom.gateway

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import pw.binom.*
import pw.binom.atomic.AtomicBoolean
import pw.binom.date.DateTime
import pw.binom.date.format.toDatePattern
import pw.binom.io.ByteBufferFactory
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
import pw.binom.gateway.properties.GatewayRuntimeProperties
import pw.binom.gateway.services.ChannelService
import pw.binom.gateway.services.GatewayControlService
import pw.binom.gateway.services.TcpConnectionFactoryImpl
import pw.binom.gateway.services.ProxyClientService
import pw.binom.io.file.*
import pw.binom.logger.Logger
import pw.binom.logger.infoSync
import pw.binom.properties.IniParser
import pw.binom.properties.serialization.PropertiesDecoder
import pw.binom.signal.Signal
import pw.binom.strong.*
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
    properties: GatewayRuntimeProperties,
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
            it.bean { ChannelService() }
            it.bean { TcpConnectionFactoryImpl() }
            it.bean { properties }
            it.bean { ProxyClientService() }
            it.bean { BinomMetrics }
            it.bean { LocalEventSystem() }
            it.bean { GatewayControlService() }
        }
    return Strong.create(baseConfig)
}

val closed = AtomicBoolean(false)

object SysLogger : InternalLog {
    val logger by Logger.ofThisOrGlobal
    override fun log(level: InternalLog.Level, file: String?, line: Int?, method: String?, text: () -> String) {
        logger.infoSync(text = "$file::$method ${text()}")
    }
}

fun main(args: Array<String>) {
    Thread {
        Thread.sleep(7.hours)
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
//    val date = "yyyy-MM-dd-HH-mm".toDatePattern().toString(DateTime.now, DateTime.systemZoneOffset)
//    InternalLog.default = GLog(File(Environment.currentExecutionPath).parent!!.relative("$date.glog"))
//    InternalLog.default = SysLogger
    val argMap = HashMap<String, String?>()
    (args
        .filter { it.startsWith("-D") }
        .map { it.removePrefix("-D") }
            +
            (Environment.workDirectoryFile.relative("config.ini")
                .takeIfFile()
                ?.readText()
                ?.lines() ?: emptyList())
            )
        .filter { it.isNotBlank() }
        .filter { !it.startsWith("#") }
        .filter { !it.startsWith(";") }
        .forEach {
            val items = it.split('=', limit = 2)
            val key = items[0]
            val value = items[1]
            argMap[key] = value
        }
    val configRoot = IniParser.parseMap(argMap)
    val properties = PropertiesDecoder.parse(
        GatewayRuntimeProperties.serializer(),
        configRoot,
    )
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
