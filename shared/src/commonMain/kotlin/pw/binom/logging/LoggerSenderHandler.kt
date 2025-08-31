package pw.binom.logging

import pw.binom.logger.Logger
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.injectOrNull
import pw.binom.strong.injectServiceList
import kotlin.coroutines.coroutineContext

class LoggerSenderHandler(
    val tags: Map<String, String>,
) : Logger.Handler {
    private val logSender by injectServiceList<LogSender>()

    init {
        BeanLifeCycle.afterInit {
            var logger = Logger.global.handler

            if (logSender.isNotEmpty()) {
                logger = if (logger == null) this else logger + this
            }
            Logger.global.handler = logger
        }
    }

    override suspend fun log(
        logger: Logger,
        level: Logger.Level,
        text: String?,
        trace: String?,
        exception: Throwable?
    ) {
        val contextTags = coroutineContext[LogTags]?.tags ?: emptyMap()
        logSender.forEach {
            it.send(
                tags = Variables.variables() +
                        Variables.getVariablesOfObject(logger) +
                        mapOf(
                            "app" to "proxy-server",
                            "logger_name" to logger.pkg,
                        ) + this.tags + contextTags,
                message = text,
                exception = exception,
                loggerName = logger.pkg,
            )
        }
    }

    override fun logSync(
        logger: Logger,
        level: Logger.Level,
        text: String?,
        trace: String?,
        exception: Throwable?
    ) {
        logSender.forEach {
            it.send(
                tags = mapOf(
                    "app" to "proxy-server",
                    "logger_name" to logger.pkg,
                ) + this.tags,
                message = text,
                exception = exception,
                loggerName = logger.pkg,
            )
        }
    }
}
