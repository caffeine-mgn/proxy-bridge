package pw.binom.proxy.services

import pw.binom.*
import pw.binom.io.ByteBufferFactory
import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServer2
import pw.binom.network.NetworkManager
import pw.binom.pool.GenericObjectPool
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.inject

abstract class AbstractWebServerService {
    protected abstract val handler: HttpHandler
    private val networkDispatcher by inject<NetworkManager>()
    private var buffer: ByteBufferPool? = null
    private var server: HttpServer2? = null
    protected abstract fun bind(server: HttpServer2)

    private var httpServer: HttpServer2? = null

    init {
        BeanLifeCycle.afterInit {
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
        BeanLifeCycle.preDestroy {
            buffer?.close()
            server!!.asyncClose()
        }
    }
}
