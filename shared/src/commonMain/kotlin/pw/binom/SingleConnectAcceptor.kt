package pw.binom

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.multiplexer.DuplexChannel
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resume

@OptIn(ExperimentalAtomicApi::class)
abstract class SingleConnectAcceptor : ConnectionAcceptor {

    private var current: DuplexChannel? = null
    private val listeners = HashSet<CancellableContinuation<Unit>>()
    private val closed = AtomicBoolean(false)

    override fun close() {
        closed.store(true)
        current?.close()
    }

    protected abstract suspend fun createConnection(onClose: () -> Unit): DuplexChannel

    private fun resetWaters() {
        val ll = HashSet(listeners)
        listeners.clear()
        ll.forEach {
            it.resume(Unit)
        }
    }

    override suspend fun connection(): DuplexChannel {
        while (true) {
            if (closed.load()) {
                throw IllegalStateException("Acceptor closed")
            }
            if (current == null) {
                val connection = createConnection {
                    current = null
                    resetWaters()
                }
                current = connection
                return connection
            }

            suspendCancellableCoroutine { continuation ->
                listeners += continuation
                continuation.invokeOnCancellation {
                    listeners -= continuation
                }
            }
        }
    }
}
