package pw.binom.multiplexer

interface Multiplexer : AutoCloseable {
    suspend fun accept(): DuplexChannel
    suspend fun createChannel(): DuplexChannel
}
