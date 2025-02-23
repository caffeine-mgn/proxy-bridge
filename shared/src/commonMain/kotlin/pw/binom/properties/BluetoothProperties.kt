package pw.binom.properties

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import pw.binom.properties.serialization.annotations.PropertiesPrefix


@PropertiesPrefix("bluetooth")
@Serializable
data class BluetoothProperties(
    val server: Boolean = false,
    val client: String? = null,
    val reconnectDelay: Duration = 10.seconds,
)
