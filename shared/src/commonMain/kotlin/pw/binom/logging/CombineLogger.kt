package pw.binom.logging

import pw.binom.logger.Logger

class CombineLogger(val a: Logger.Handler, val b: Logger.Handler) : Logger.Handler {
    override fun logSync(logger: Logger, level: Logger.Level, text: String?, trace: String?, exception: Throwable?) {
        a.logSync(logger = logger, level = level, text = text, trace = trace, exception = exception)
        b.logSync(logger = logger, level = level, text = text, trace = trace, exception = exception)
    }

    override suspend fun log(
        logger: Logger,
        level: Logger.Level,
        text: String?,
        trace: String?,
        exception: Throwable?
    ) {
        a.log(logger = logger, level = level, text = text, trace = trace, exception = exception)
        b.log(logger = logger, level = level, text = text, trace = trace, exception = exception)
    }
}

operator fun Logger.Handler.plus(other: Logger.Handler) =
    CombineLogger(a = this, b = other)
