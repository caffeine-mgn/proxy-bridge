package pw.binom.subchannel

import pw.binom.AsyncReCloseable
import pw.binom.frame.FrameChannel

abstract class AbstractCommandClient : AsyncReCloseable() {
    protected abstract val channel: FrameChannel

    protected inline fun <T> asClosed(func: () -> T): T {
        check(!isClosed) { "Already closed" }
        val r = func()
        makeClosed()
        return r
    }

    override suspend fun realAsyncClose() {
        channel.asyncClose()
    }
}
