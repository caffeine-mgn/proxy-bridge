package pw.binom

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pw.binom.atomic.AtomicBoolean
import pw.binom.logger.Logger
import pw.binom.logger.warn
import kotlin.time.Duration.Companion.seconds

object SlowCoroutineDetect {
    @Deprecated(level = DeprecationLevel.ERROR, message = "SlowCoroutineDetect is no longer needed")
    @PublishedApi
    internal val logger by Logger.ofThisOrGlobal

    @Suppress("DEPRECATION_ERROR")
    inline fun <T> detect(msg: String, func: () -> T): T {
        val stackTrace = Throwable()
        val finished = AtomicBoolean(false)
        GlobalScope.launch {
            delay(1.seconds)
            if (!finished.getValue()) {
                logger.warn(text = "Query is slow: $msg", exception = stackTrace)
            }
        }

        return try {
            func()
        } finally {
            finished.setValue(true)
        }
    }
}
