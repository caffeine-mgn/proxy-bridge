package pw.binom.transport

import pw.binom.io.Closeable

interface VirtualManager : Closeable {
    suspend fun connect(serviceId: Int): MultiplexSocket
    fun defineService(serviceId: Int, implements: Service)
    fun undefineService(serviceId: Int)
}
