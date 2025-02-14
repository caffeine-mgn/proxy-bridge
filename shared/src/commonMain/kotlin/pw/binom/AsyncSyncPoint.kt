package pw.binom

import pw.binom.concurrency.SpinLock
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AsyncSyncPoint {

    private var waiter: Continuation<Unit>? = null
    private var status = Status.EMPTY
    private val lock = SpinLock()

    private enum class Status {
        LOCKED,
        EMPTY,
        RELEASED,
    }

    suspend fun lock() {
        lock.lock()
        when (status) {
            Status.LOCKED -> {
                lock.unlock()
                throw IllegalStateException("SyncPoint already locked")
            }

            Status.EMPTY -> {
                status = Status.LOCKED
                suspendCoroutine<Unit> {
                    waiter = it
                    lock.unlock()
                }
            }

            Status.RELEASED -> {
                status = Status.EMPTY
                lock.unlock()
            }
        }
    }

    fun release() {
        lock.lock()
        when (status) {
            Status.LOCKED -> {
                val w = waiter!!
                this.waiter = null
                status = Status.EMPTY
                lock.unlock()
                w.resume(Unit)
            }

            Status.EMPTY -> {
                status = Status.RELEASED
                lock.unlock()
            }

            Status.RELEASED -> {
                lock.unlock()
                throw IllegalStateException("SyncPoint already released")
            }
        }
    }
}
