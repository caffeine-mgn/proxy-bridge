package pw.binom

import pw.binom.atomic.AtomicBoolean
import pw.binom.frame.FrameChannel
import pw.binom.io.AsyncCloseable

abstract class AbstractCommandClient : AsyncCloseable {
    protected abstract val channel: FrameChannel
    private val closed = AtomicBoolean(false)
    val isClosed
        get()=closed.getValue()

    protected inline fun <T> asClosed(func: () -> T): T {
        check(!isClosed) { "Already closed" }
        return func()
    }

    override suspend fun asyncClose() {
        if (!closed.compareAndSet(false, true)) {
            channel.asyncClose()
        }
    }
}
