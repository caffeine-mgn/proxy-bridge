package pw.binom.proxy

import pw.binom.collections.removeIf

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
