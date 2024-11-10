package pw.binom

import pw.binom.io.AsyncChannel

interface TcpConnectionFactory {
    suspend fun connect(host: String, port: Int): AsyncChannel
}
