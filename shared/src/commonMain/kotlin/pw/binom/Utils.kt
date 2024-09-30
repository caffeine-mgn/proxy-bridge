package pw.binom

import kotlinx.coroutines.CoroutineScope
import pw.binom.collections.removeIf
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

suspend fun currentScope(): CoroutineScope {
    val ctx = coroutineContext
    return object : CoroutineScope {
        override val coroutineContext: CoroutineContext
            get() = ctx
    }
}

class ArgCounter {
    private var name: String? = null

    companion object {
        fun calc(func: ArgCounter.() -> Unit) {
            val counter = ArgCounter()
            func(counter)
            counter.flush()
        }
    }

    fun add(
        value: Any?,
        name: String,
    ) {
        value ?: return
        if (this.name != null) {
            throw RuntimeException("Can't set argument $name: argument ${this.name} already passed")
        }
        this.name = name
    }

    fun flush() {
        if (name == null) {
            throw RuntimeException("No any argument pass")
        }
    }
}

inline fun <T> MutableCollection<T>.extract(crossinline condition: (T) -> Boolean): List<T> {
    val result = ArrayList<T>()
    removeIf {
        val remove = condition(it)
        if (remove) {
            result.add(it)
        }
        remove
    }
    return if (result.isEmpty()) {
        emptyList()
    } else {
        result
    }
}
