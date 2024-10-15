package pw.binom.gateway

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import pw.binom.*
import pw.binom.gateway.behaviors.ConnectTcpBehavior
import pw.binom.gateway.behaviors.DefaultTcpConnectionFactory
import pw.binom.io.AsyncChannel
import pw.binom.io.AsyncCloseable
import pw.binom.io.socket.InetSocketAddress
import pw.binom.io.useAsync
import pw.binom.network.MultiFixedSizeThreadNetworkDispatcher
import pw.binom.network.TcpServerConnection
import pw.binom.proxy.ChannelId
import pw.binom.coroutines.CountWater
import pw.binom.proxy.channels.TransportChannel

class Context : AsyncCloseable {
    val client = TestingProxyClient()
    private val nm = MultiFixedSizeThreadNetworkDispatcher(4)
    val transport = DefaultTcpConnectionFactory(nm = nm)
    private var transportChannel: TransportChannel? = null
    private var server: TcpServerConnection? = null
    private val counter = CountWater(0)
    override suspend fun asyncClose() {
        counter.await()
        transport.asyncClose()
        server?.closeAnyway()
        nm.closeAnyway()
    }

    private fun bindServer(): TcpServerConnection {
        val server = server ?: nm.bindTcp(InetSocketAddress.resolve(host = "127.0.0.1", port = 0))
        this.server = server
        return server
    }

    fun tcpOneConnect(func: suspend AsyncChannel.() -> Unit) {
        counter.increment()
        val server = bindServer()
        GlobalScope.launch(nm) {
            try {
                server.accept().useAsync { func(it) }
            } finally {
                counter.decrement()
            }
        }
    }

    fun transport(id: ChannelId = ChannelId(""), func: suspend AsyncChannel.() -> Unit) {
        transportChannel = VirtualTransportChannel.create(id = id, func = func)
    }

    suspend fun connect(host: String = "127.0.0.1", port: Int): ConnectTcpBehavior? {
        val channel = transportChannel ?: throw IllegalStateException("TransportChannel not defined")
        return ConnectTcpBehavior.start(
            client = client,
            from = channel,
            host = host,
            port = port,
            tcpConnectionFactory = transport,
        )
    }

    suspend fun connect(): ConnectTcpBehavior? {
        val server = server ?: throw IllegalStateException("Server is not defined")
        return connect(
            host = "127.0.0.1",
            port = server.port,
        )
    }

    companion object {
        suspend fun use(func: suspend Context.() -> Unit) {
            Context().useAsync {
                func(it)
            }
        }
    }
}
