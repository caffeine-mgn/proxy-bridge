package pw.binom.proxy.node

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.properties.Properties
import pw.binom.*
import pw.binom.io.use
import pw.binom.network.MultiFixedSizeThreadNetworkDispatcher
import pw.binom.network.NetworkManager
import pw.binom.proxy.node.handlers.*
import pw.binom.signal.Signal
import pw.binom.strong.Strong
import pw.binom.strong.bean

suspend fun startProxyNode(properties: RuntimeClientProperties, networkManager: NetworkManager): Strong {
    val baseConfig = Strong.config {
        it.bean { networkManager }
        it.bean { ExternalHandler() }
        it.bean { properties }
        it.bean { ClientControlHandler() }
        it.bean { ClientTransportTcpHandler() }
        it.bean { ClientTransportWsHandler() }
        it.bean { ClientService() }
        it.bean { ProxyHandler() }
        it.bean { InternalHandler() }
        it.bean { ClientPoolOutputHandler() }
        it.bean { ClientPoolInputHandler() }
        it.bean { InternalWebServerService() }
        it.bean { ExternalWebServerService() }
    }
    println("Starting node")
    return Strong.create(baseConfig, BaseConfig)
}

fun main(args: Array<String>) {
    val params = args.filter { it.startsWith("-D") }.associate {
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
