package pw.binom.logging

import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.injectOrNull

class SQLiteLogSender : LogSender {

    private val sqlLogAppender by injectOrNull<SQLiteLogAppender>()

    override fun send(tags: Map<String, String>, message: String?, exception: Throwable?, loggerName: String) {
        sqlLogAppender?.insert(
            module = loggerName,
            method = "",
            message = (message ?: "") + (exception?.stackTraceToString() ?: ""),
        )
    }
}
