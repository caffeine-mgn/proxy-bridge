package pw.binom.multiplexer

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.io.Buffer

interface DuplexChannel {
    val income: ReceiveChannel<Buffer>
    val outcome: SendChannel<Buffer>
}
