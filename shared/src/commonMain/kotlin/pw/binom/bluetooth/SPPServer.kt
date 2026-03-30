package pw.binom.bluetooth

import pw.binom.io.BluetoothConnection

interface SPPServer : AutoCloseable {
    suspend fun accept(): BluetoothConnection
}
