package pw.binom.utils

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
fun AtomicBoolean.lock() {
    while (!compareAndSet(false, true)) {
        // Do nothing
    }
}

@OptIn(ExperimentalAtomicApi::class)
fun AtomicBoolean.unlock() {
    store(false)
}

@OptIn(ExperimentalAtomicApi::class)
inline fun <T> AtomicBoolean.locking(func: () -> T): T {
    lock()
    try {
        return func()
    } finally {
        unlock()
    }
}
