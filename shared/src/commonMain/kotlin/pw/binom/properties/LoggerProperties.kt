package pw.binom.properties

import kotlinx.serialization.Serializable
import pw.binom.properties.serialization.annotations.PropertiesPrefix
import pw.binom.url.URL

@Serializable
@PropertiesPrefix("logger")
class LoggerProperties(
    val promtail: PromtailSender? = null,
    val dbSaver: DbSaver? = null,
) {
    @Serializable
    class PromtailSender(
        val url: String,
    )

    @Serializable
    class DbSaver(
        val file: String,
    )

    val isCustomLogger
        get() = promtail != null || dbSaver != null
}
