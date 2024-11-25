package pw.binom.logging

import pw.binom.logger.Logger

class SQLiteLogger(private val sql: SQLiteLogAppender) : Logger.Handler {
    override fun logSync(logger: Logger, level: Logger.Level, text: String?, trace: String?, exception: Throwable?) {
        sql.insert(
            module = logger.pkg,
            method = "",
            message = (text ?: "") + (exception?.stackTraceToString() ?: ""),
        )
    }
}
