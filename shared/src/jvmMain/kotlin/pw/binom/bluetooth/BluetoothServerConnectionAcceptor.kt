package pw.binom.bluetooth

import pw.binom.ConnectionAcceptor
import pw.binom.multiplexer.DuplexChannel

class BluetoothServerConnectionAcceptor(val adapter: BluetoothAdapter) : ConnectionAcceptor {
    private val server = adapter.listenSPP()
    override suspend fun connection(): DuplexChannel = server.accept()

    override fun close() {
        server.close()
    }
}
