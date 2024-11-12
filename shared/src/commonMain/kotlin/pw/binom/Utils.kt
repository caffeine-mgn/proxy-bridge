package pw.binom

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pw.binom.collections.removeIf
import pw.binom.io.socket.ListenFlags
import pw.binom.io.socket.MulticastUdpSocket
import pw.binom.io.socket.TcpClientSocket
import pw.binom.io.socket.TcpNetServerSocket
import pw.binom.io.socket.UdpNetSocket
import pw.binom.network.MulticastUdpConnection
import pw.binom.network.NetworkManager
import pw.binom.network.TcpConnection
import pw.binom.network.TcpServerConnection
import pw.binom.network.UdpConnection
import pw.binom.strong.ServiceProvider
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

suspend fun currentScope(): CoroutineScope {
    val ctx = coroutineContext
    return object : CoroutineScope {
        override val coroutineContext: CoroutineContext
            get() = ctx
    }
}

fun <T> GlobalScope.mergeChannels(vararg channels: ReceiveChannel<T>) : ReceiveChannel<T> {
    return produce {
        channels.forEach {
            launch { it.consumeEach { send(it) }}
        }
    }
}

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

class ArgCounter {
    private var name: String? = null

    companion object {
        fun calc(func: ArgCounter.() -> Unit) {
            val counter = ArgCounter()
            func(counter)
            counter.flush()
        }
    }

    fun add(
        value: Any?,
        name: String,
    ) {
        value ?: return
        if (this.name != null) {
            throw RuntimeException("Can't set argument $name: argument ${this.name} already passed")
        }
        this.name = name
    }

    fun flush() {
        if (name == null) {
            throw RuntimeException("No any argument pass")
        }
    }
}

inline fun <T> MutableCollection<T>.extract(crossinline condition: (T) -> Boolean): List<T> {
    val result = ArrayList<T>()
    removeIf {
        val remove = condition(it)
        if (remove) {
            result.add(it)
        }
        remove
    }
    return if (result.isEmpty()) {
        emptyList()
    } else {
        result
    }
}
