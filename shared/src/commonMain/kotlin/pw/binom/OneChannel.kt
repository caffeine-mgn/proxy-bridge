package pw.binom

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.atomic.AtomicBoolean
import pw.binom.collections.LinkedList
import pw.binom.collections.removeIf
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.Closeable
import kotlin.coroutines.resume

class Exchange<T> : Closeable {
    private val stateLock = SpinLock()
    suspend fun put(value: T) {
        stateLock.lock()
        if (closed.getValue()) {
            stateLock.unlock()
            throw IllegalStateException("Exchange closed")
        }
        val reader = readers.removeLastOrNull()
        if (reader != null) {
            stateLock.unlock()
            reader.resume(value)
        } else {
            suspendCancellableCoroutine { con ->
                con.invokeOnCancellation {
                    stateLock.synchronize {
                        writers.removeIf { it.first == con }
                    }
                }
                writers.addFirst(con to value)
                stateLock.unlock()
            }
        }
    }

    private val readers = LinkedList<CancellableContinuation<T>>()
    private val writers = LinkedList<Pair<CancellableContinuation<Unit>, T>>()
    private val closed = AtomicBoolean(false)

    suspend fun pop(): T {
        stateLock.lock()
        if (closed.getValue()) {
            stateLock.unlock()
            throw IllegalStateException("Exchange closed")
        }
        val writer = writers.removeLastOrNull()
        if (writer != null) {
            writer.first.resume(Unit)
            stateLock.unlock()
            return writer.second
        } else {
            return suspendCancellableCoroutine {
                readers.addFirst(it)
                stateLock.unlock()
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        stateLock.synchronize {
            readers.forEach {
                it.cancel()
            }
            writers.forEach {
                it.first.cancel()
            }
        }
    }
}
