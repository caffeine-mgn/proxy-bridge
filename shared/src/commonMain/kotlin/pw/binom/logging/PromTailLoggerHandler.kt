package pw.binom.logging

import pw.binom.logger.Logger
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.injectOrNull

class PromTailLoggerHandler(
    val tags: Map<String, String>,
) : Logger.Handler {
    private val logSender by injectOrNull<LogSender>()
    private var handler: Logger.Handler? = null

    init {
        BeanLifeCycle.afterInit {
            if (logSender != null) {
                handler = Logger.global.handler
                Logger.global.handler = this
            }
        }
    }

    override suspend fun log(
        logger: Logger,
        level: Logger.Level,
        text: String?,
        trace: String?,
        exception: Throwable?
    ) {
        logSender?.send(
            tags = Variables.variables() +
                    Variables.getVariablesOfObject(logger) +
                    mapOf(
                        "app" to "proxy-server",
                        "logger_name" to logger.pkg,
                    ) + this.tags,
            message = text,
            exception = exception,
            loggerName = logger.pkg,
        )
        handler?.log(logger = logger, level = level, text = text, trace = trace, exception = exception)
    }

    override fun logSync(
        logger: Logger,
        level: Logger.Level,
        text: String?,
        trace: String?,
        exception: Throwable?
    ) {
        logSender?.send(
            tags = mapOf(
                "app" to "proxy-server",
                "logger_name" to logger.pkg,
            ) + this.tags,
            message = text,
            exception = exception,
            loggerName = logger.pkg,
        )
        handler?.logSync(logger = logger, level = level, text = text, trace = trace, exception = exception)
    }
}
