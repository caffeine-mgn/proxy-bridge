package pw.binom.logging

import pw.binom.*
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.logger.INFO
import pw.binom.logger.Logger
import pw.binom.logger.SEVERE
import pw.binom.logger.WARNING

class InternalLoggerToLogger : InternalLog {

    private val map = HashMap<String?, Logger>()
    private val lock = SpinLock()

    override val enabled: Boolean
        get() = true

    override fun log(level: InternalLog.Level, file: String?, line: Int?, method: String?, text: () -> String) {
        val logger = lock.synchronize {
            map.getOrPut(file) { Logger.getLogger(file ?: "root") }
        }
        val l = when (level) {
            InternalLog.Level.INFO -> Logger.INFO
            InternalLog.Level.WARNING -> Logger.WARNING
            InternalLog.Level.CRITICAL,
            InternalLog.Level.FATAL,
            InternalLog.Level.ERROR -> Logger.SEVERE
        }
        logger.logSync(
            level = l,
            text = text(),
        )
    }

    override fun <T> tx(func: (InternalLog.Transaction) -> T): T =
        func(InternalLog.Transaction.NULL)
}
