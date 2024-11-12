package pw.binom.logging

import pw.binom.logger.Logger

interface LogSender {

    fun send(
        tags: Map<String, String>,
        message: String?,
        exception: Throwable?,
        loggerName: String,
    )
}
