package pw.binom

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.collections.LinkedList
import pw.binom.concurrency.SpinLock

class AsyncQueue<T> {
    private val lock = SpinLock()
    private val values = LinkedList<T>()
    private val waters = LinkedList<CancellableContinuation<T>>()
    fun push(value: T) {
        lock.lock()
        val w = waters.removeFirstOrNull()
        if (w != null) {
            lock.unlock()
            w.resumeWith(Result.success(value))
        } else {
            values.addLast(value)
            lock.unlock()
        }
    }

    suspend fun get(): T {
        lock.lock()
        return if (values.isEmpty()) {
            suspendCancellableCoroutine<T> { cont ->
                waters.addFirst(cont)
                lock.unlock()
            }
        } else {
            val value = values.removeFirst()
            lock.unlock()
            value
        }
    }
}
