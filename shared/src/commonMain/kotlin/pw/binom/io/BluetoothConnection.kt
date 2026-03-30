package pw.binom.io

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.io.Buffer
import pw.binom.multiplexer.DuplexChannel

class BluetoothConnection(
    override val income: ReceiveChannel<Buffer>,
    override val outcome: SendChannel<Buffer>,
    private val connect: AutoCloseable
) : DuplexChannel, AutoCloseable {
    companion object;

    override fun close() {
        connect.close()
    }
}

