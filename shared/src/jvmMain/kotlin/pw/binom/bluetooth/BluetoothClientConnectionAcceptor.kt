package pw.binom.bluetooth

import pw.binom.*
import pw.binom.multiplexer.DuplexChannel
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class BluetoothClientConnectionAcceptor(
    private val adapter: BluetoothAdapter,
    private val bluetoothAddress: String,
) : SingleConnectAcceptor() {

    override suspend fun createConnection(onClose: () -> Unit): DuplexChannel =
        adapter.connectSPP(bluetoothAddress, onClose)
}
