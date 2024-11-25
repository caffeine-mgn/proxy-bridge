package pw.binom.logging

interface LogSender {

    fun send(
        tags: Map<String, String>,
        message: String?,
        exception: Throwable?,
        loggerName: String,
    )
}
