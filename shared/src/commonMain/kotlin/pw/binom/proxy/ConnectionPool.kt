package pw.binom.proxy

import kotlinx.coroutines.runBlocking
import pw.binom.atomic.AtomicBoolean
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.date.DateTime
import pw.binom.io.AsyncChannel
import pw.binom.io.AsyncCloseable
import pw.binom.thread.Thread
import kotlin.time.Duration

class ConnectionPool(
    private val maxIdle: Duration,
    private val checkInterval: Duration,
) : AsyncCloseable {


    class Record(val dateTime: DateTime, val channel: AsyncChannel)

    private val records = HashMap<Int, Record>()
    private val lock = SpinLock()
    private val closed = AtomicBoolean(false)
    private val thread = Thread {
        while (!closed.getValue()) {
            if (checkInterval.isPositive()) {
                Thread.sleep(checkInterval.inWholeMilliseconds)
            }
            runBlocking {
                cleanup()
            }
        }
    }

    init {
        thread.start()
    }

    override suspend fun asyncClose() {
        closed.setValue(true)
    }

    private suspend fun cleanup(): Int {
        val forRemove = ArrayList<AsyncChannel>()
        val now = DateTime.now
        lock.synchronize {
            val it = records.iterator()
            while (it.hasNext()) {
                val e = it.next()
                if (now - e.value.dateTime < maxIdle) {
                    forRemove += e.value.channel
                    it.remove()
                }
            }
        }
        forRemove.forEach {
            it.asyncCloseAnyway()
        }
        return forRemove.size
    }

    fun add(id: Int, channel: AsyncChannel) {
        val r = Record(dateTime = DateTime.now, channel = channel)
        lock.synchronize {
            records[id] = r
        }
    }

    fun getAny(): Pair<Int, AsyncChannel>? {
        val now = DateTime.now
        lock.synchronize {
            val it = records.iterator()
            while (it.hasNext()) {
                val e = it.next()
                if (now - e.value.dateTime < maxIdle) {
                    it.remove()
                    return e.key to e.value.channel
                }
            }
        }
        return null
    }
}
