package pw.binom
/*

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.collections.LinkedList
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AsyncQueue<T> {
    private var water: CancellableContinuation<T>? = null
    private val lock = SpinLock()
    private val list = LinkedList<T>()
    fun push(value: T) {
        lock.lock()
        val water = water
        if (water != null) {
            this.water = null
            lock.unlock()
            water.resume(value)
        } else {
            list.addFirst(value)
            lock.unlock()
        }
    }

    fun locking(func: (MutableList<T>) -> Unit) {
        lock.synchronize {
            func(list)
        }
    }

    suspend fun pop(): T {
        lock.lock()
        return if (list.isEmpty()) {
            suspendCancellableCoroutine<T> {
                water = it
                lock.unlock()
            }
        } else {
            val value = list.removeLast()
            lock.unlock()
            value
        }
    }

    fun clearWithValue(e: T) {
        lock.lock()
        list.clear()
        val water = water
        this.water = null
        lock.unlock()
        water?.resume(e)
    }

    fun clearWithException(e: Throwable = NoSuchElementException()) {
        lock.lock()
        list.clear()
        val water = water
        this.water = null
        lock.unlock()
        water?.resumeWithException(e)
    }
}
*/
