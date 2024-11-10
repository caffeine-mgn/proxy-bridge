package pw.binom.proxy

import pw.binom.TcpConnectionFactory
import pw.binom.io.AsyncChannel

class TcpConnectionFactoryImpl:TcpConnectionFactory {
    override suspend fun connect(host: String, port: Int): AsyncChannel {
        TODO("Not yet implemented")
    }
}
