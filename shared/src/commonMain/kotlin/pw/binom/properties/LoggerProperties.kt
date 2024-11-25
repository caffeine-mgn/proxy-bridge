package pw.binom.properties

import kotlinx.serialization.Serializable
import pw.binom.properties.serialization.annotations.PropertiesPrefix
import pw.binom.url.URL

@Serializable
@PropertiesPrefix("logger")
class LoggerProperties(
    val promtail: PromtailSender? = null,
    val db: String? = null,
) {
    @Serializable
    class PromtailSender(
        val url: String,
        val app: String,
    )

    val isCustomLogger
        get() = promtail != null || db != null
}
