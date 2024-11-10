package pw.binom.gateway.behaviors

import pw.binom.*
import pw.binom.io.AsyncChannel
import pw.binom.io.AsyncCloseable
import pw.binom.io.socket.DomainSocketAddress
import pw.binom.network.MultiFixedSizeThreadNetworkDispatcher
import pw.binom.network.NetworkManager
import pw.binom.network.tcpConnect

class DefaultTcpConnectionFactory(val nm: NetworkManager) : TcpConnectionFactory, AsyncCloseable {
    override suspend fun connect(host: String, port: Int): AsyncChannel =
        nm.tcpConnect(DomainSocketAddress(host = host, port = port).resolve())

    override suspend fun asyncClose() {
    }
}
