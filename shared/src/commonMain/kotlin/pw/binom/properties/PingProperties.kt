package pw.binom.properties

import kotlinx.serialization.Serializable
import pw.binom.properties.serialization.annotations.PropertiesPrefix
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
@PropertiesPrefix("ping")
data class PingProperties(
    val interval: Duration = 30.seconds,
    val timeout: Duration = 10.seconds,
    val size: Int = 32,
    val pingFailCount: Int = 3,
)
