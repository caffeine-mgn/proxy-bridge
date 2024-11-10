package pw.binom

interface Holder<T> {
    val value: T
    fun release()
}
