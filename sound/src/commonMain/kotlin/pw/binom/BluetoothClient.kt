package pw.binom

import kotlin.time.Duration

expect class BluetoothClient {
    constructor(
        addressAndChannel: String,
        reconnectDelay: Duration,
    )
}
