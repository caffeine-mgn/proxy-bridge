package pw.binom.proxy

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import pw.binom.*
import pw.binom.communicate.CommunicateRepository
import pw.binom.communicate.tcp.TcpCommunicatePair
import pw.binom.frame.PackageSize
import pw.binom.io.file.File
import pw.binom.io.file.LocalFileSystem
import pw.binom.io.file.readText
import pw.binom.io.file.takeIfFile
import pw.binom.io.file.workDirectoryFile
import pw.binom.io.use
import pw.binom.logger.Logger
import pw.binom.logger.WARNING
import pw.binom.network.MultiFixedSizeThreadNetworkDispatcher
import pw.binom.network.NetworkManager
import pw.binom.properties.IniParser
import pw.binom.properties.serialization.PropertiesDecoder
import pw.binom.proxy.controllers.*
import pw.binom.proxy.properties.ProxyProperties
import pw.binom.proxy.server.ClientService
import pw.binom.proxy.services.ExternalWebServerService
import pw.binom.proxy.services.InternalWebServerService
import pw.binom.services.VirtualChannelService
import pw.binom.signal.Signal
import pw.binom.strong.LocalEventSystem
import pw.binom.strong.Strong
import pw.binom.strong.bean

suspend fun startProxyNode(
    properties: ProxyProperties,
    networkManager: NetworkManager,
): Strong {
    val baseConfig =
        Strong.config {
            it.bean { networkManager }
            it.bean { ExternalHandler() }
            it.bean { properties }
            it.bean { ClientControlHandler() }
            it.bean { ClientTransportTcpHandler() }
            it.bean { ClientTransportWsHandler() }
            it.bean { ClientService() }
            it.bean { ProxyHandler() }
            it.bean { LocalEventSystem() }
            it.bean { ServiceInfoHandler() }
            it.bean { InternalHandler() }
            it.bean { ClientPoolOutputHandler() }
            it.bean { ClientPoolInputHandler() }
            it.bean { InternalWebServerService() }
            it.bean { ExternalWebServerService() }
            it.bean { BinomMetrics }
//            it.bean { GatewayClientService() }
//            it.bean { ServerControlService() }
            it.bean { PrometheusController() }
            it.bean { TcpCommunicatePair() }
            it.bean { CommunicateRepository() }
            it.bean { TcpConnectionFactoryImpl() }
            it.bean { VirtualChannelService(bufferSize = PackageSize(properties.bufferSize)) }
            val pool = ByteBufferPool(size = DEFAULT_BUFFER_SIZE)
            it.bean { pool }
            it.bean(name = "LOCAL_FS") { LocalFileSystem(root = File("/"), byteBufferPool = pool) }
        }
    println("Starting node")
    return Strong.create(baseConfig)
}

fun main(args: Array<String>) {
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

    runBlocking {
        MultiFixedSizeThreadNetworkDispatcher(Environment.availableProcessors).use { networkManager ->
            val strong = startProxyNode(properties = properties, networkManager = networkManager)
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
