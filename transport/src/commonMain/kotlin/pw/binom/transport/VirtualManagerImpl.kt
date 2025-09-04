package pw.binom.transport

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import pw.binom.atomic.AtomicBoolean
import pw.binom.io.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

class VirtualManagerImpl(
    val multiplexer: Multiplexer,
    private val scope: CoroutineScope = GlobalScope,
    private val context: CoroutineContext = EmptyCoroutineContext,
) : VirtualManager {
    private val connections = HashMap<Int, MultiplexSocketImpl>()
    private val accepting = HashMap<Int, CancellableContinuation<MultiplexSocket>>()
    private val closed = AtomicBoolean(false)
    private val services = HashMap<Int, Service>()

    private val job = scope.launch(context) {
        while (isActive) {
            val event = multiplexer.read()
            when (event) {
                is Multiplexer.Event.Accepted -> accepted(event.channelId)
                is Multiplexer.Event.Closing -> close(event.channelId)
                is Multiplexer.Event.Data -> incomeData(event)
                is Multiplexer.Event.New -> income(
                    channelId = event.channelId,
                    serviceId = event.serviceId
                )

                is Multiplexer.Event.Refused -> refused(channelId = event.channelId)
            }
        }
    }

    suspend fun join() {
        job.join()
    }

    override suspend fun connect(serviceId: Int): MultiplexSocket {
        checkClosed()
        val channelId = multiplexer.new(serviceId = serviceId)
        return suspendCancellableCoroutine<MultiplexSocket> {
            it.invokeOnCancellation {
                accepting.remove(channelId)
            }
            accepting[channelId] = it
        }
    }

    override fun defineService(serviceId: Int, implements: Service) {
        checkClosed()
        check(!services.containsKey(serviceId)) { "Service with id $services already defined" }
        services[serviceId] = implements
        implements.onStart(this)
    }

    override fun undefineService(serviceId: Int) {
        if (closed.getValue()) {
            return
        }
        val implements = services.remove(serviceId)
        check(implements != null) { "Service with id $services not defined" }
        implements.onStop(this)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        job.cancel()
        accepting.values.forEach {
            it.resumeWithException(ClosedException())
        }
        accepting.clear()
        connections.values.forEach {
            it.close()
        }
        connections.clear()
        services.values.forEach {
            it.onStop(this)
        }
        services.clear()
    }

    private fun refused(channelId: Int) {
        val continuation = accepting.remove(channelId) ?: return
        continuation.resumeWithException(IOException("Connection refused"))
    }

    private suspend fun accepted(channelId: Int) {
        val continuation = accepting.remove(channelId)
        if (continuation == null) {
            multiplexer.close(channelId)
            return
        }
        val socket = MultiplexSocketImpl(channelId)
        connections[channelId] = socket
        continuation.resume(socket)
    }

    private suspend fun close(channelId: Int) {
        connections.remove(channelId)?.internalClose()
    }

    private suspend fun incomeData(data: Multiplexer.Event.Data) {
        val socket = connections[data.channelId]
        if (socket == null) {
            multiplexer.close(data.channelId)
            return
        }
        socket.input.income.send(data.data)
    }

    private suspend fun income(channelId: Int, serviceId: Int) {
        if (connections.containsKey(channelId)) {
            multiplexer.close(channelId)
            return
        }
        val implement = services[serviceId]
        if (implement == null) {
            multiplexer.refused(channelId)
        } else {
            val socket = MultiplexSocketImpl(channelId)
            connections[channelId] = socket
            multiplexer.accepted(channelId)
            try {
                implement.income(socket)
            } catch (_: Throwable) {
                connections.remove(channelId)
                multiplexer.close(channelId)
            }
        }
    }

    private fun checkClosed() {
        check(!closed.getValue()) { "Connection already closed" }
    }

    private class AsyncInputImpl(
        val channelId: Int,
        val onClose: suspend () -> Unit
    ) : AsyncInput {

        private val data = Channel<ByteBuffer>(onUndeliveredElement = { it.close() })
        val income: SendChannel<ByteBuffer>
            get() = data
        private val closed = AtomicBoolean(false)
        private var current: ByteBuffer? = null

        override val available: Available
            get() = if (closed.getValue()) {
                Available.NOT_AVAILABLE
            } else {
                val current = current
                if (current != null && current.hasRemaining) {
                    Available.of(current.remaining)
                } else {
                    Available.UNKNOWN
                }
            }

        override suspend fun read(dest: ByteBuffer): DataTransferSize {
            println("AsyncInputImpl::read #$channelId need read ${dest.remaining}, current.remaining=${current?.remaining}")
            if (!dest.hasRemaining) {
                return DataTransferSize.EMPTY
            }
            while (true) {
                if (closed.getValue()) {
                    return DataTransferSize.CLOSED
                }
                var current = this.current
                if (current == null) {
                    println("AsyncInputImpl::read #$channelId buffer missing. Waiting next")
                    val newBuffer = data.receive()
                    current = newBuffer
                    this.current = current
                    println("AsyncInputImpl::read #$channelId new buffer got. new buffer remaining=${newBuffer.remaining}")
                }
                val wrote = dest.write(current)
                if (!current.hasRemaining) {
                    println("AsyncInputImpl::read #$channelId old buffer finished. old buffer capacity=${current.capacity}")
                    current.close()
                    this.current = null
                }
                if (wrote.isAvailable) {
                    println("AsyncInputImpl::read #$channelId returned $wrote")
                    return wrote
                }
            }
        }

        override suspend fun asyncClose() {
            if (!closed.compareAndSet(false, true)) {
                return
            }
            data.close(ClosedException())
            current?.close()
            current = null
            onClose()
        }
    }

    private class AsyncOutputImpl(
        val channelId: Int,
        val multiplexer: Multiplexer,
        val onClose: suspend () -> Unit
    ) : AsyncOutput {
        private val data = ByteBuffer(multiplexer.packageSize)
        private val closed = AtomicBoolean(false)

        override suspend fun write(data: ByteBuffer): DataTransferSize {
            if (closed.getValue()) {
                return DataTransferSize.CLOSED
            }
            val l = this.data.write(data)
            checkFlush()
            return l
        }

        override suspend fun asyncClose() {
            if (closed.compareAndSet(false, true)) {
                flush()
                onClose()
            }
        }

        suspend fun checkFlush() {
            if (!data.hasRemaining) {
                flush()
            }
        }

        override suspend fun flush() {
            if (closed.getValue()) {
                return
            }
            if (data.position == 0) {
                return
            }
            data.flip()
            multiplexer.send(
                channel = channelId,
                data = data,
            )
            data.clear()
        }
    }

    private inner class MultiplexSocketImpl(private val channelId: Int) : MultiplexSocket {
        override val input = AsyncInputImpl(
            channelId = channelId,
            onClose = {
                inputClosed = true
                checkClosed()
            },
        )
        override val output = AsyncOutputImpl(
            channelId = channelId,
            multiplexer = multiplexer,
            onClose = {
                outputClosed = true
                checkClosed()
            }
        )

        private var inputClosed = false
        private var outputClosed = false

        private suspend fun checkClosed() {
            if (!inputClosed || !outputClosed) {
                return
            }
            multiplexer.close(channelId)
        }

        suspend fun internalClose() {
            input.asyncClose()
            output.asyncClose()
        }

        override fun close() {
            scope.launch(context) {
                internalClose()
            }
        }
    }
}
