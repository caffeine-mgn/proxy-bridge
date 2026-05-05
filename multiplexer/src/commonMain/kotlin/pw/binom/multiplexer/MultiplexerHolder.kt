package pw.binom.multiplexer

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class MultiplexerHolder : Multiplexer, Lazy<Multiplexer> {
    private val instance = AtomicReference<Multiplexer?>(null)
    inline fun <T> use(instance: Multiplexer, func: () -> T) =
        try {
            set(instance)
            func()
        } finally {
            remove()
        }

    fun set(instance: Multiplexer) {
        this.instance.store(instance)
    }

    fun remove() {
        instance.store(null)
    }

    override suspend fun accept(): DuplexChannel = value.accept()

    override suspend fun createChannel(): DuplexChannel = value.createChannel()

    override fun close() {
        instance.load()?.close()
    }

    override val value: Multiplexer
        get() = instance.load() ?: throw IllegalStateException("Multiplexer not defined")

    override fun isInitialized(): Boolean = instance.load() != null
}
