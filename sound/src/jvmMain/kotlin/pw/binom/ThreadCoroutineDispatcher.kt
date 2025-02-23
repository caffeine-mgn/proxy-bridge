package pw.binom

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.atomic.AtomicBoolean
import pw.binom.concurrency.ReentrantLock
import pw.binom.concurrency.synchronize
import pw.binom.io.Closeable
import kotlin.coroutines.CoroutineContext

class ThreadCoroutineDispatcher(name: String) : CoroutineDispatcher(), Closeable {
    private val readyForWriteListener = BatchExchange<Runnable>()

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = Thread.currentThread() !== thread

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    private val closed = AtomicBoolean(false)
    val isClosed
        get() = closed.getValue()

    private val thread =
        Thread({
            while (!closed.getValue()) {
                lock.synchronize {
                    if (readyForWriteListener.isEmpty()) {
                        try {
                            condition.await()
                        } catch (e: InterruptedException) {
                            closed.setValue(true)
                            return@Thread
                        } catch (e: pw.binom.concurrency.InterruptedException) {
                            closed.setValue(true)
                            return@Thread
                        }
                    }
                    if (closed.getValue()) {
                        return@synchronize
                    }
                    readyForWriteListener.popAll {
                        it.forEach {
                            try {
                                it.run()
                            } catch (e: Throwable) {
                                // TODO сделать нормальный вывод ошибки
                            }
                        }
                    }
                }
            }
        }, name)

    init {
        thread.start()
    }

    suspend fun <T> asyncExecute(block: () -> T): T =
        suspendCancellableCoroutine { con ->
            readyForWriteListener.push {
                con.resumeWith(runCatching { block() })
            }
            lock.synchronize {
                condition.signalAll()
            }
        }

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        readyForWriteListener.push(block)
        lock.synchronize {
            condition.signalAll()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        thread.interrupt()
        lock.synchronize {
            condition.signalAll()
        }
        if (thread.threadId() != Thread.currentThread().threadId()) {
            thread.join()
        }
    }
}
