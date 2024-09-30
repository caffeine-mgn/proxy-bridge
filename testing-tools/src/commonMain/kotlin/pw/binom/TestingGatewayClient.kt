package pw.binom

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.atomic.AtomicBoolean
import pw.binom.collections.LinkedList
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.gateway.GatewayClient
import pw.binom.io.ClosedException
import pw.binom.proxy.dto.ControlEventDto
import pw.binom.proxy.dto.ControlRequestDto
import kotlin.coroutines.resume

class TestingGatewayClient:GatewayClient {

    private val closed = AtomicBoolean(false)
    private val internalCommands = LinkedList<ControlRequestDto>()
    fun popCmd() = internalCommands.removeLast()

    private val events = LinkedList<ControlEventDto>()
    private val eventsWater = LinkedList<CancellableContinuation<ControlEventDto>>()
    private val lock = SpinLock()

    fun clearCommands() {
        internalCommands.clear()
    }

    val isClosed
        get() = closed.getValue()

    val commandCount
        get() = internalCommands.size

    fun putEvent(cmd: ControlEventDto) {
        checkClosed()
        val water = lock.synchronize { eventsWater.removeFirstOrNull() }
        if (water != null) {
            water.resume(cmd)
        } else {
            lock.synchronize {
                events.push(cmd)
            }
        }
    }

    override suspend fun sendCmd(request: ControlRequestDto) {
        checkClosed()
        lock.synchronize {
            internalCommands.addFirst(request)
        }
    }

    private fun checkClosed() {
        if (closed.getValue()) {
            throw ClosedException()
        }
    }

    override suspend fun receiveEvent(): ControlEventDto {
        checkClosed()
        lock.lock()
        if (events.isEmpty()) {
            return suspendCancellableCoroutine {
                eventsWater.addLast(it)
                lock.unlock()
            }
        }
        val value = events.removeFirst()
        lock.unlock()
        return value
    }

    override suspend fun asyncClose() {
        closed.setValue(true)
    }
}
