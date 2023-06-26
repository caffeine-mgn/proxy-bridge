package pw.binom.proxy

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.properties.Properties
import pw.binom.Environment
import pw.binom.availableProcessors
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.create
import pw.binom.network.MultiFixedSizeThreadNetworkDispatcher
import pw.binom.network.NetworkManager
import pw.binom.proxy.handlers.*
import pw.binom.signal.Signal
import pw.binom.strong.Strong
import pw.binom.strong.bean
import pw.binom.strong.inject

fun main(args: Array<String>) {
    val params = args.filter { it.startsWith("-D") }.associate {
        val items = it.removePrefix("-D").split('=', limit = 2)
        items[0] to items[1]
    }
    val properties = Properties.decodeFromStringMap(RuntimeProperties.serializer(), params)

    runBlocking {
        val baseConfig = Strong.config {
            it.bean { MultiFixedSizeThreadNetworkDispatcher(Environment.availableProcessors) }
            it.bean { ExternalHandler() }
            it.bean { properties }
            it.bean { ClientControlHandler() }
            it.bean { ClientTransportHandler() }
            it.bean { ClientService() }
            it.bean { ProxyHandler() }
            it.bean { InternalHandler() }
            it.bean { InternalWebServerService() }
            it.bean { ExternalWebServerService() }
        }
        val mainCoroutineContext = coroutineContext
        val strong = Strong.create(baseConfig)
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
