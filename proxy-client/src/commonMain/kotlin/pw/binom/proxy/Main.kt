package pw.binom.proxy

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.properties.Properties
import pw.binom.Environment
import pw.binom.availableProcessors
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.create
import pw.binom.io.socket.TcpClientSocket
import pw.binom.io.socket.TcpNetServerSocket
import pw.binom.io.socket.UdpNetSocket
import pw.binom.io.use
import pw.binom.network.*
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

fun main(args: Array<String>) {
    val params = args.filter { it.startsWith("-D") }.associate {
        val items = it.removePrefix("-D").split('=', limit = 2)
        items[0] to items[1]
    }
    val properties = Properties.decodeFromStringMap(RuntimeProperties.serializer(), params)
    runBlocking {
        val baseConfig = Strong.config {
            it.bean { MultiFixedSizeThreadNetworkDispatcher(Environment.availableProcessors) }
            it.bean {
                val nm = inject<NetworkManager>()
                HttpClient.create(networkDispatcher = nm.asInstance())
            }
            it.bean { ControlService() }
            it.bean { TransportService() }
            it.bean { properties }
        }
        val mainCoroutineContext = coroutineContext
        val strong = Strong.create(baseConfig)
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
