package pw.binom.proxy

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import pw.binom.*
import pw.binom.config.BluetoothConfig
import pw.binom.config.CommandsConfig
import pw.binom.config.FileSystemConfig
import pw.binom.config.LoggerConfig
import pw.binom.frame.PackageSize
import pw.binom.fs.RemoteFileSystem
import pw.binom.io.file.*
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.create
import pw.binom.io.use
import pw.binom.logger.Logger
import pw.binom.logger.WARNING
import pw.binom.network.MultiFixedSizeThreadNetworkDispatcher
import pw.binom.network.NetworkManager
import pw.binom.properties.*
import pw.binom.properties.serialization.PropertiesDecoder
import pw.binom.proxy.controllers.*
import pw.binom.proxy.properties.ProxyProperties
import pw.binom.proxy.services.ExternalWebServerService
import pw.binom.proxy.services.InternalWebServerService
import pw.binom.services.ClientService
import pw.binom.services.VirtualChannelServiceImpl2
import pw.binom.services.VirtualChannelServiceIncomeService
import pw.binom.signal.Signal
import pw.binom.strong.LocalEventSystem
import pw.binom.strong.Strong
import pw.binom.strong.bean
import pw.binom.strong.inject
import pw.binom.url.toPath
import pw.binom.webdav.FileSystemWebDavHandler

suspend fun startProxyNode(
    properties: ProxyProperties,
    loggerProperties: LoggerProperties,
    pingProperties: PingProperties,
    networkManager: NetworkManager,
    fileConfig: FileSystemProperties,
    bluetooth: BluetoothProperties,
): Strong {
    val baseConfig =
        Strong.config {
            it.bean { networkManager }
            it.bean { ExternalHandler() }
            it.bean { VirtualChannelServiceIncomeService() }
            it.bean { properties }
            it.bean { pingProperties }
            it.bean { ClientService() }
            it.bean { BenchmarkHandler() }
            it.bean { ClientControlHandler() }
//            it.bean { ClientTransportTcpHandler() }
            it.bean { ClientTransportWsHandler() }
//            it.bean { ClientService() }
            it.bean { ProxyHandler() }
            it.bean { LocalEventSystem() }
            it.bean { ServiceInfoHandler() }
            it.bean { InternalHandler() }
            it.bean { loggerProperties }
//            it.bean { ClientPoolOutputHandler() }
//            it.bean { ClientPoolInputHandler() }
            it.bean { InternalWebServerService() }
            it.bean { ExternalWebServerService() }
            it.bean { BinomMetrics }
            it.bean { GCService() }
            it.bean {
                FileSystemWebDavHandler(
                    fs = inject<RemoteFileSystem>(),
                    global = "".toPath,
                )
            }
//            it.bean { GatewayClientService() }
//            it.bean { ServerControlService() }
            it.bean { PrometheusController() }
//            it.bean { TcpCommunicatePair() }
//            it.bean { CommunicateRepository() }
            it.bean { TcpConnectionFactoryImpl() }

            it.bean {
                val nm = inject<NetworkManager>()
                HttpClient.create(networkDispatcher = nm.asInstance())
            }
//            it.bean { VirtualChannelServiceImpl(bufferSize = PackageSize(properties.bufferSize)) }
            it.bean {
                VirtualChannelServiceImpl2(
                    bufferSize = PackageSize(properties.bufferSize),
                    serverMode = true,
                    networkManager = inject(),
                )
            }
            val pool = ByteBufferPool(size = DEFAULT_BUFFER_SIZE)
            it.bean { pool }
            it.bean(name = "LOCAL_FS") { LocalFileSystem(root = File("/"), byteBufferPool = pool) }
        }
    println("Starting node")
    return Strong.create(
        baseConfig,
        CommandsConfig(),
        LoggerConfig(loggerProperties),
        FileSystemConfig(fileConfig),
        BluetoothConfig(bluetooth),
    )
}

fun main(args: Array<String>) {
//    val sql = SQLiteLogAppender(file = File("log.db"))
    Logger.getLogger("Strong.Starter").level = Logger.WARNING
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
    val properties = PropertiesDecoder.parse(ProxyProperties.serializer(), configRoot)
    val loggerProperties = PropertiesDecoder.parse(LoggerProperties.serializer(), configRoot)
    val pingProperties = PropertiesDecoder.parse(PingProperties.serializer(), configRoot)
    val fileConfig = PropertiesDecoder.parse(FileSystemProperties.serializer(), configRoot)
    val bluetoothProperties = PropertiesDecoder.parse(BluetoothProperties.serializer(), configRoot)
    runBlocking {
        MultiFixedSizeThreadNetworkDispatcher(Environment.availableProcessors).use { networkManager ->
            val strong = startProxyNode(
                properties = properties,
                networkManager = networkManager,
                loggerProperties = loggerProperties,
                pingProperties = pingProperties,
                fileConfig = fileConfig,
                bluetooth = bluetoothProperties,
            )
            val mainCoroutineContext = coroutineContext
            Signal.handler {
                if (it.isInterrupted) {
                    if (!strong.isDestroying && !strong.isDestroyed) {
                        GlobalScope.launch(mainCoroutineContext) {
                            strong.destroy()
                        }
                    }
                }
            }
            strong.awaitDestroy()
        }
    }
}
