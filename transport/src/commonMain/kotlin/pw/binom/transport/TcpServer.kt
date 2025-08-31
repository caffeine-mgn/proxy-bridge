package pw.binom.transport

import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import pw.binom.*
import pw.binom.io.socket.InetSocketAddress
import pw.binom.io.use
import pw.binom.network.MultiFixedSizeThreadNetworkDispatcher
import pw.binom.network.NetworkManager

object TcpServer {
    fun start(networkManager: NetworkManager, port: Int, services: (VirtualManagerImpl) -> Unit) {
        networkManager.bindTcp(InetSocketAddress.resolve(host = "0.0.0.0", port = port)).use { server ->
            runBlocking {
                while (isActive) {
                    val client = server.accept()
                    networkManager.launch {
                        val virtualManager = Manager.create(
                            input = client,
                            output = client,
                            maxPackageSize = 1400,
                            scope = networkManager,
                            isServer = true,
                        )
                        services(virtualManager)
                        virtualManager.join()
                    }
                }
            }
        }
    }

    fun start(port: Int, services: (VirtualManagerImpl) -> Unit) {
        MultiFixedSizeThreadNetworkDispatcher(Environment.availableProcessors).use { networkManager ->
            start(
                port = port,
                networkManager = networkManager,
                services = services,
            )
        }
    }
}
