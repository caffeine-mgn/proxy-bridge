package pw.binom.config

import pw.binom.*
import pw.binom.properties.BluetoothProperties
import pw.binom.strong.Strong
import pw.binom.strong.bean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun BluetoothConfig(config: BluetoothProperties) = Strong.config {
    if (config.server) {
        it.bean { BluetoothServer() }
    }
    if (config.client != null) {
        it.bean {
            BluetoothClient(
                addressAndChannel = config.client!!,
                reconnectDelay = config.reconnectDelay,
            )
        }
    }
}
