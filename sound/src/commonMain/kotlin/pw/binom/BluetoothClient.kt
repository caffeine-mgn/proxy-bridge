package pw.binom

import pw.binom.io.BluetoothConnection
import kotlin.time.Duration

expect class BluetoothClient {
    companion object {
        suspend fun connect(addressAndChannel:String): BluetoothConnection
    }

    constructor(
        addressAndChannel: String,
        reconnectDelay: Duration,
    )
}
