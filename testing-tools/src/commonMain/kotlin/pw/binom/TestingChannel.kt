package pw.binom

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.atomic.AtomicBoolean
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.*
import pw.binom.logger.Logger
import pw.binom.logger.info
import kotlin.coroutines.resume

class TestingChannel : AsyncChannel {
    override fun toString(): String = "TestingChannel@${hashCode()}"
    override val available: Available
        get() = lock.synchronize { Available.of(data.size) }
    private val data = ByteArrayOutput()
    private var readWater: CancellableContinuation<DataTransferSize>? = null
    private var readInto: ByteBuffer? = null
    private val lock = SpinLock()
    private val logger = Logger.getLogger("MyChannel ${hashCode()}")
    private var waitUntilEnd: CancellableContinuation<Unit>? = null
    suspend fun waitUntilEnd() {
        val rem = lock.synchronize {
            data.size
        }
        if (rem <= 0) {
            return
        }
        suspendCancellableCoroutine { waitUntilEnd = it }
    }

    private val closed = AtomicBoolean(false)


    override suspend fun asyncClose() {
        if (closed.compareAndSet(false, true)) {
            lock.synchronize {
                logger.info("Close channel with data: ${data.size}")
                data.close()
                readWater?.resume(DataTransferSize.CLOSED)
                readWater = null
                readInto = null
            }
        }
    }

    override suspend fun flush() {
        if (closed.getValue()) {
            throw StreamClosedException()
        }
    }

    override suspend fun read(dest: ByteBuffer): DataTransferSize {
        if (closed.getValue()) {
            logger.info("READ1. ${DataTransferSize.CLOSED}")
            return DataTransferSize.CLOSED
        }
        if (!dest.hasRemaining) {
            logger.info("READ2. ${DataTransferSize.EMPTY}")
            return DataTransferSize.EMPTY
        }
        lock.lock()
        if (data.size == 0) {
            val waitUntilEnd = waitUntilEnd
            if (waitUntilEnd != null && !waitUntilEnd.isCancelled) {
                this.waitUntilEnd = null
                lock.unlock()
                waitUntilEnd.resume(Unit)
                logger.info("READ3 by has waitUntilEnd. ${DataTransferSize.CLOSED}")
                return DataTransferSize.CLOSED
            }
            logger.info("No data. Waiting")
            val result = suspendCancellableCoroutine {
                readInto = dest
                readWater = it
                lock.unlock()
            }
            if (result.isClosed) {
                lock.lock()
                if (data.size == 0) {
                    lock.unlock()
                    waitUntilEnd?.resume(Unit)
                } else {
                    lock.unlock()
                }
            }
            logger.info("READ4 by resume of suspend. $result")
            return result
        } else {
            val size = minOf(dest.remaining, data.size)
            data.locked {
                it.reset(0, size)
                it.read(dest = dest)
            }
            data.removeFirst(size)
            lock.unlock()
            if (data.size == 0) {
                lock.synchronize {
                    val waitUntilEnd = waitUntilEnd
                    this.waitUntilEnd = null
                    waitUntilEnd
                }?.resume(Unit)
//                logger.info("READ5. ${DataTransferSize.CLOSED}")
//                return DataTransferSize.CLOSED
            }
            logger.info("READ6 data exist. ${DataTransferSize.ofSize(size)}")
            return DataTransferSize.ofSize(size)
        }
    }

    override suspend fun write(data: ByteBuffer): DataTransferSize {
        if (closed.getValue()) {
            logger.info("WRITE1 ${DataTransferSize.CLOSED}")
            return DataTransferSize.CLOSED
        }
        if (!data.hasRemaining) {
            logger.info("WRITE2 ${DataTransferSize.EMPTY}")
            return DataTransferSize.EMPTY
        }
        lock.lock()
        val readWater = readWater
        if (readWater != null) {
            val ee = data.read(readInto!!)
            val e = ee
            this.readWater = null
            this.readInto = null
            lock.unlock()
            readWater.resume(e)
            logger.info("WRITE3 $e")
            return e
        } else {
            val r = this.data.write(data)
            lock.unlock()
            logger.info("WRITE4 $r")
            return r
        }
    }
}
