package pw.binom.proxy.gateway

import pw.binom.*
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.socket.SocketAddress
import pw.binom.logger.Logger
import pw.binom.network.NetworkManager
import pw.binom.network.tcpConnect
import pw.binom.proxy.ChannelBridge
import pw.binom.proxy.gateway.properties.RuntimeProperties
import pw.binom.strong.Strong
import pw.binom.strong.inject

class TransportService : Strong.DestroyableBean {
    private val httpClient by inject<HttpClient>()
    private val networkManager by inject<NetworkManager>()
    private val runtimeProperties by inject<RuntimeProperties>()
    private val connectionFactory by inject<ConnectionFactory>()
    private val lock = SpinLock()
    private val connections = HashSet<TransportClient>()
    private var closing = false
    private val logger by Logger.ofThisOrGlobal

    suspend fun connectTcp(
        id: Int,
        address: SocketAddress,
    ) {
        require(!closing) { "Service is closing" }
        val transportConnection = connectionFactory.connect(id)
        val socket =
            try {
                networkManager.tcpConnect(address = address.resolve())
            } catch (e: Throwable) {
                e.beforeThrow {
                    transportConnection.asyncCloseAnyway()
                }
            }

        ChannelBridge.create(
            local = socket,
            remote = transportConnection,
            logger = Logger.getLogger("Transport #$id $address"),
            bufferSize = runtimeProperties.bufferSize,
            localName = "client",
            id = id,
            scope = networkManager
        )
    }

    suspend fun connect(
        id: Int,
        address: SocketAddress,
    ) {
        connectTcp(
            id = id,
            address = address
        )
    }

    override suspend fun destroy(strong: Strong) {
        closing = true
        lock.synchronize { HashSet(connections) }.forEach {
            it.asyncCloseAnyway()
        }
    }
}
