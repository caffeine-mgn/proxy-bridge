package pw.binom.frame

import pw.binom.io.StreamClosedException
import kotlin.jvm.JvmInline

@JvmInline
@Suppress("UNCHECKED_CAST")
value class FrameResult<T> private constructor(private val raw: Any?) {
    private object CLOSED
    companion object {
        fun <T> closed() = FrameResult<T>(CLOSED)
        fun <T> of(value: T) = FrameResult<T>(value)
    }

    inline fun ensureNotClosed(): T {
        check(isNotClosed) { "Connection closed" }
        return getOrThrow()
    }

    inline fun ensureNotClosed(func: () -> String): T {
        check(isNotClosed, func)
        return getOrThrow()
    }

    val isClosed
        get() = raw === CLOSED

    val isNotClosed
        get() = raw !== CLOSED

    fun getOrThrow(): T {
        if (raw === CLOSED) {
            throw StreamClosedException()
        }
        return raw as T
    }

    val valueOrNull: T?
        get() {
            if (raw === CLOSED) {
                return null
            }
            return raw as T
        }
}
