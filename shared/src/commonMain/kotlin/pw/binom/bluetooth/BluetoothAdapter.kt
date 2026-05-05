package pw.binom.bluetooth

import pw.binom.io.BluetoothConnection

expect interface BluetoothAdapter {
    companion object {
        fun getAdapters(): List<BluetoothAdapter>
    }

    fun getLocalAddress(): String
    fun listenSPP(): SPPServer
    suspend fun connectSPP(address: String, onClose: () -> Unit = {}): BluetoothConnection
}
