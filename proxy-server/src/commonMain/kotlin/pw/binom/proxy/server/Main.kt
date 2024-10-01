package pw.binom.proxy.server

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.properties.Properties
import pw.binom.*
import pw.binom.io.use
import pw.binom.network.MultiFixedSizeThreadNetworkDispatcher
import pw.binom.network.NetworkManager
import pw.binom.proxy.server.handlers.*
import pw.binom.proxy.server.properties.RuntimeClientProperties
import pw.binom.proxy.server.services.GatewayClientService
import pw.binom.proxy.server.services.ServerControlService
import pw.binom.signal.Signal
import pw.binom.strong.LocalEventSystem
import pw.binom.strong.Strong
import pw.binom.strong.bean

suspend fun startProxyNode(
    properties: RuntimeClientProperties,
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
            it.bean { GatewayClientService() }
            it.bean { ServerControlService() }
            it.bean { PrometheusController() }
        }
    println("Starting node")
    return Strong.create(baseConfig, BaseConfig)
}

fun main(args: Array<String>) {
    val params =
        args.filter { it.startsWith("-D") }.associate {
            val items = it.removePrefix("-D").split('=', limit = 2)
            items[0] to items[1]
        }
    val properties = Properties.decodeFromStringMap(RuntimeClientProperties.serializer(), params)

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
