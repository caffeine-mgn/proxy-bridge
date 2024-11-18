package pw.binom.coroutines

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import pw.binom.collections.InternalApi
import pw.binom.collections.LinkedList
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration

class SimpleAsyncLock2 : AsyncLock {
    private val waters = LinkedList<CancellableContinuation<Unit>>()
    override val isLocked: Boolean
        get() = state.synchronize { locked }

    private val state = SpinLock()
    private var locked = false

    @OptIn(InternalApi::class)
    suspend fun lock() {
        state.lock()
        if (locked) {
            suspendCancellableCoroutine {
                val node = waters.addLast(it)
                state.unlock()
                it.invokeOnCancellation {
                    state.synchronize {
                        waters.unlink(node)
                    }
                }
            }
        } else {
            locked = true
            state.unlock()
        }
    }

    fun unlock() {
        state.synchronize {
            val con = waters.removeFirstOrNull()
            if (con == null) {
                locked = false
            }
            con
        }?.resume(Unit)
    }


    override suspend fun <T> synchronize(lockingTimeout: Duration, func: suspend () -> T): T {
        if (lockingTimeout.isInfinite()) {
            lock()
        } else {
            withTimeout(lockingTimeout) {
                lock()
            }
        }
        return try {
            func()
        } finally {
            unlock()
        }
    }

    override fun throwAll(e: Throwable): Int {
        val list = state.synchronize {
            val newList = ArrayList(waters)
            waters.clear()
            newList
        }
        list.forEach {
            it.resumeWithException(e)
        }
        return list.size
    }

    override suspend fun <T> trySynchronize(
        lockingTimeout: Duration,
        func: suspend () -> T
    ): AsyncLock.SynchronizeResult<T> {
        if (lockingTimeout.isInfinite()) {
            lock()
            return AsyncLock.SynchronizeResult.locked(func())
        }
        val locked = withTimeoutOrNull(lockingTimeout) {
            lock()
        } != null

        if (!locked) {
            return AsyncLock.SynchronizeResult.notLocked()
        }
        return AsyncLock.SynchronizeResult.locked(func())
    }

}
