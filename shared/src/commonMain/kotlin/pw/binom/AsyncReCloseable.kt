package pw.binom

import pw.binom.atomic.AtomicBoolean
import pw.binom.io.AsyncCloseable
import pw.binom.io.ClosedException

abstract class AsyncReCloseable : AsyncCloseable {
    private val closed = AtomicBoolean(false)
    protected val isClosed: Boolean
        get() = closed.getValue()

    protected fun makeClosed() {
        closed.setValue(true)
    }

    protected open fun ensureNotClosed(){
        if (closed.getValue()) {
            throw ClosedException()
        }
    }

    protected abstract suspend fun realAsyncClose()

    final override suspend fun asyncClose() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        realAsyncClose()
    }
}
