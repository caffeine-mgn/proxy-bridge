package pw.binom.properties

import pw.binom.multiplexer.DuplexChannel

interface OutcomeService {
    val name: String
    suspend fun createChannel(): DuplexChannel
}
