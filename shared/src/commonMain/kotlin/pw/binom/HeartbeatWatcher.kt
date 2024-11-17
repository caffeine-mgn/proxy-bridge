package pw.binom

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.date.DateTime
import pw.binom.io.Closeable
import kotlin.time.Duration

class HeartbeatWatcher(
    val interval: Duration,
    val timeout: Duration = interval * 1.5,
    val onTimeout: (() -> Unit)? = null,
    val onTimeoutStart: (() -> Unit)? = null,
    val onTimeoutEnd: (() -> Unit)? = null,
) : Closeable {
    val job = GlobalScope.launch {
        while (coroutineContext.isActive) {
            delay(interval)
            val latency = lock.synchronize { DateTime.now - lastCheck }
            if (latency > timeout) {
                if (!timeouted) {
                    onTimeoutStart?.invoke()
                }
                timeouted = true
                onTimeout?.invoke()
            } else {
                if (timeouted) {
                    onTimeoutEnd?.invoke()
                    timeouted = false
                }
            }
        }
    }

    private var timeouted = false
    private var lastCheck = DateTime.now
    private val lock = SpinLock()

    fun fire() {
        lock.synchronize {
            lastCheck = DateTime.now
        }
    }

    override fun close() {
        if (!job.isCancelled) {
            job.cancel()
        }
    }
}
