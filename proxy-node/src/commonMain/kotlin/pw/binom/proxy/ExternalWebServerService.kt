package pw.binom.proxy

import pw.binom.ByteBufferPool
import pw.binom.DEFAULT_BUFFER_SIZE
import pw.binom.io.ByteBufferFactory
import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServer2
import pw.binom.network.NetworkManager
import pw.binom.pool.GenericObjectPool
import pw.binom.proxy.handlers.ExternalHandler
import pw.binom.strong.Strong
import pw.binom.strong.inject

class ExternalWebServerService : AbstractWebServerService() {

    private val properties by inject<RuntimeProperties>()

    override val handler: HttpHandler
        get() = externalHandler

    override fun bind(server: HttpServer2) {
        properties.externalBinds.forEach {
            server.listen(it)
        }
    }

    private val externalHandler by inject<ExternalHandler>()

}