package pw.binom.multiplexer

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MultiplexerHolder : Multiplexer, Lazy<Multiplexer> {
    private val lock = Mutex()
    @Volatile
    @PublishedApi internal
    var instance: Multiplexer? = null

    inline fun <T> use(mux: Multiplexer, func: () -> T) =
        try {
            instance = mux
            func()
        } finally {
            instance = null
        }

    fun set(mux: Multiplexer) {
        runBlocking {
            lock.withLock {
                instance = mux
            }
        }
    }

    fun remove() {
        runBlocking {
            lock.withLock {
                instance = null
            }
        }
    }

    override suspend fun accept(): DuplexChannel = value.accept()

    override suspend fun createChannel(): DuplexChannel = value.createChannel()

    override fun close() {
        val m = runBlocking {
            lock.withLock {
                val m = instance
                instance = null
                m
            }
        }
        m?.close()
    }

    override val value: Multiplexer
        get() = instance ?: throw IllegalStateException("Multiplexer not defined")

    override fun isInitialized(): Boolean = instance != null
}
