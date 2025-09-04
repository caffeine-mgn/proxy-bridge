package pw.binom.transport.io

import pw.binom.http.client.factory.NetSocketFactory
import pw.binom.transport.VirtualManager
import pw.binom.transport.services.TcpBridgeService

class VirtualSocketFactory(val manager: VirtualManager) : NetSocketFactory {
    override suspend fun connect(host: String, port: Int) =
        TcpBridgeService.connect(
            manager = manager,
            host = host,
            port = port,
        )
}
