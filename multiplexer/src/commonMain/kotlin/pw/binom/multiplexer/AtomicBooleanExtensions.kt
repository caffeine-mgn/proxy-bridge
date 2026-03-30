package pw.binom.multiplexer

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
fun AtomicBoolean.lock() {
    while (true) {
        if (compareAndSet(false, true)) {
            break
        }
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
