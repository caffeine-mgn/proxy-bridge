package pw.binom

import pw.binom.multiplexer.DuplexChannel

interface ConnectionAcceptor : AutoCloseable {
    suspend fun connection(): DuplexChannel
}
