package pw.binom

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pw.binom.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration

inline suspend fun <T> timeoutChecker(timeout: Duration, noinline onTimeout: () -> Unit, block: suspend () -> T): T {
    val finished = AtomicBoolean(false)
    val watingJob = GlobalScope.launch(coroutineContext) {
        while (!finished.getValue()) {
            delay(timeout)
            if (!finished.getValue()) {
                onTimeout()
            }
        }
    }
    return try {
        block()
    } finally {
        watingJob.cancel()
        finished.setValue(true)
    }
}
