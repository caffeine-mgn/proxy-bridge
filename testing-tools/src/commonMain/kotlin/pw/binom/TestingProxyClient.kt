package pw.binom

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.atomic.AtomicBoolean
import pw.binom.collections.LinkedList
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.Closeable
import pw.binom.io.ClosedException
import pw.binom.proxy.ProxyClient
import pw.binom.proxy.dto.ControlEventDto
import pw.binom.proxy.dto.ControlRequestDto
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class TestingProxyClient : ProxyClient {
    private val closed = AtomicBoolean(false)
    private val internalEvents = LinkedList<ControlEventDto>()
    fun popEvent() = internalEvents.removeLast()
    fun clearEvents() {
        internalEvents.clear()
    }

    val isClosed
        get() = closed.getValue()

    val eventCount
        get() = internalEvents.size

    private val commands = LinkedList<ControlRequestDto>()
    private val commandsWater = LinkedList<CancellableContinuation<ControlRequestDto>>()
    private val lock = SpinLock()

    fun putCommand(cmd: ControlRequestDto) {
        checkClosed()
        val water = lock.synchronize { commandsWater.removeFirstOrNull() }
        if (water != null) {
            water.resume(cmd)
        } else {
            lock.synchronize {
                commands.addLast(cmd)
            }
        }
    }

    override suspend fun sendEvent(event: ControlEventDto) {
        checkClosed()
        lock.synchronize {
            internalEvents.addFirst(event)
        }
    }

    override suspend fun receiveCommand(): ControlRequestDto {
        checkClosed()
        lock.lock()
        if (commands.isEmpty()) {
            return suspendCancellableCoroutine {
                commandsWater.addLast(it)
                lock.unlock()
            }
        }
        val value = commands.removeFirst()
        lock.unlock()
        return value
    }

    private fun checkClosed() {
        if (closed.getValue()) {
            throw ClosedException()
        }
    }

    override suspend fun asyncClose() {
        closed.setValue(true)
    }
}
