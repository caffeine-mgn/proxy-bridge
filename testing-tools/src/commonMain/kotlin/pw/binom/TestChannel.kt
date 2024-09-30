package pw.binom

import kotlinx.coroutines.*
import pw.binom.*
import pw.binom.atomic.AtomicBoolean
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.*
import pw.binom.logger.Logger
import pw.binom.logger.info
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun testChannel(func: suspend AsyncChannel.() -> Unit): AsyncChannel {

    val read = TestingChannel()
    val write = TestingChannel()

    val t1 = AsyncChannel.create(
        input = read,
        output = write
    )
    val t2 = AsyncChannel.create(
        input = write,
        output = read
    )
    println("external: ${t2.hashCode()}, internal: ${t1.hashCode()}, read:${read.hashCode()}, write:${write.hashCode()}")
    GlobalScope.launch {
        try {
            func(t1)
            write.waitUntilEnd()
        } finally {
            t1.asyncClose()
            t2.asyncClose()
        }
    }

    return t2
}

private class TestChannel : AsyncChannel {
    override val available: Int
        get() = -1
    val closeable = AtomicBoolean(false)

    val forRead = ByteArrayOutput()
    var forReadWater: CancellableContinuation<Unit>? = null
    var forWrite = ByteArrayOutput()
    var forWriteWater: CancellableContinuation<Unit>? = null
    var waitForEndRead: CancellableContinuation<Unit>? = null
    val logger by Logger.ofThisOrGlobal
    val lock = SpinLock()

    suspend fun waitUntilReadFinish() {
        suspendCancellableCoroutine<Unit> {
            waitForEndRead = it
        }
        closeable.setValue(true)
    }

    override suspend fun asyncClose() {
        closeable.setValue(true)
    }

    override suspend fun flush() {
        if (closeable.getValue()) {
            throw StreamClosedException()
        }
    }

    override suspend fun read(dest: ByteBuffer): DataTransferSize {
        logger.info("Try read ${dest.remaining} bytes")
        if (closeable.getValue()) {
            return DataTransferSize.CLOSED
        }
        if (!dest.hasRemaining) {
            return DataTransferSize.EMPTY
        }
        if (forWrite.size == 0) {
            lock.lock()
            if (forWriteWater != null) {
                lock.unlock()
                throw IllegalStateException("already wating")
            }
            suspendCancellableCoroutine {
                it.invokeOnCancellation {
                    forWriteWater?.resumeWithException(it ?: kotlin.coroutines.cancellation.CancellationException())
                }
                forWriteWater = it
                lock.unlock()
            }
        }
        val maxSize = minOf(forWrite.size, dest.remaining)
        forWrite.data.holdState {
            it.position = 0
            it.limit = maxSize
            logger.info("coping ${it.remaining} to out")
            it.copyTo(dest)
        }
        forWrite.removeFirst(maxSize)
        if (forWrite.size == 0) {
            waitForEndRead?.resume(Unit)
        }
        return DataTransferSize.ofSize(maxSize)
    }

    override suspend fun write(data: ByteBuffer): DataTransferSize {
        logger.info("Try write ${data.remaining} bytes")
        if (!data.hasRemaining) {
            logger.info("No data for write")
            return DataTransferSize.EMPTY
        }
        val r = forRead.write(data)
        lock.lock()
        val forReadWater = forReadWater
        if (forReadWater != null) {
            logger.info("need to resume reading. size: ${forRead.size}")
            this.forReadWater = null
            lock.unlock()
            forReadWater.resume(Unit)
        } else {
            lock.unlock()
        }
        logger.info("wrote $r")
        return r
    }

}

private class TestChannelContext(private val channel: TestChannel) : AsyncChannel {
    private var closed = AtomicBoolean(false)
    val logger by Logger.ofThisOrGlobal
    override val available: Int
        get() = if (closed.getValue()) 0 else -1

    override suspend fun asyncClose() {
        closed.setValue(true)
    }

    override suspend fun flush() {
        check(!closed.getValue()) { "Function closed" }
        if (channel.closeable.getValue()) {
            throw StreamClosedException()
        }
    }

    override suspend fun read(dest: ByteBuffer): DataTransferSize {
        check(!closed.getValue()) { "Function closed" }
        if (channel.closeable.getValue()) {
            return DataTransferSize.CLOSED
        }
        if (channel.forRead.size == 0) {
            logger.info("No data for read. Wait.....")
            suspendCancellableCoroutine {
                it.invokeOnCancellation {
                    channel.lock.synchronize {
                        val ddd = channel.forReadWater
                        if (ddd != null) {
                            channel.forReadWater = null
                        }
                        ddd
                    }?.cancel(it ?: kotlin.coroutines.cancellation.CancellationException())
                }
                channel.lock.synchronize {
                    channel.forReadWater = it
                }
            }
        }
        val maxLen = minOf(dest.remaining, channel.forRead.size)
        logger.info("Input data done! maxLen=$maxLen")
        channel.forRead.data.holdState {
            it.position = 0
            it.limit = maxLen
            it.copyTo(dest)
        }
        channel.forRead.removeFirst(maxLen)
        return DataTransferSize.ofSize(maxLen)
    }

    override suspend fun write(data: ByteBuffer): DataTransferSize {
        check(!closed.getValue()) { "Function closed" }
        if (channel.closeable.getValue()) {
            return DataTransferSize.CLOSED
        }
        check(!closed.getValue()) { "Function closed" }
        val r = channel.forWrite.write(data)
        val forWriteWater = channel.forWriteWater
        if (forWriteWater != null) {
            channel.forWriteWater = null
            forWriteWater.resume(Unit)
        }
        return r
    }
}
