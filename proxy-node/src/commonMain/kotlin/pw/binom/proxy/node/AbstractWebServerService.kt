package pw.binom.proxy.node

import pw.binom.*
import pw.binom.io.ByteBufferFactory
import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServer2
import pw.binom.network.NetworkManager
import pw.binom.pool.GenericObjectPool
import pw.binom.strong.Strong
import pw.binom.strong.inject

abstract class AbstractWebServerService : Strong.InitializingBean, Strong.DestroyableBean {
    protected abstract val handler: HttpHandler
    private val networkDispatcher by inject<NetworkManager>()
    private var buffer: ByteBufferPool? = null
    private var server: HttpServer2? = null
    protected abstract fun bind(server: HttpServer2)
    override suspend fun destroy(strong: Strong) {
        buffer?.close()
        server!!.asyncClose()
    }

    private var httpServer: HttpServer2? = null

    override suspend fun init(strong: Strong) {
        val buffer = GenericObjectPool(factory = ByteBufferFactory(DEFAULT_BUFFER_SIZE))
        this.buffer = buffer
        val server = HttpServer2(
            handler = handler,
            byteBufferPool = buffer,
            dispatcher = networkDispatcher,
        )
        try {
            bind(server)
            httpServer = server
        } catch (e: Throwable) {
            server.asyncCloseAnyway()
            throw e
        }
        this.server = server
    }
}
