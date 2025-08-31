package pw.binom

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import pw.binom.io.AsyncCloseable
import pw.binom.io.Closeable
import pw.binom.io.use
import pw.binom.network.MultiFixedSizeThreadNetworkDispatcher
import pw.binom.network.NetworkManager

abstract class AbstractIT {
    interface TestContext {
        val networkManager: NetworkManager
        val channel: TestingFrameChannel
        fun launch(func: suspend CoroutineScope.() -> Unit)
        fun <T : Closeable> closing(value: T): T
        fun <T : AsyncCloseable> closing(value: T): T
    }

    private class TestContextImpl(override val networkManager: NetworkManager) : TestContext, AsyncCloseable {
        val jobs = ArrayList<Job>()
        val closable1 = ArrayList<Closeable>()
        val closable2 = ArrayList<AsyncCloseable>()
        override val channel = TestingFrameChannel()

        override fun launch(func: suspend CoroutineScope.() -> Unit) {
            jobs += networkManager.launch(block = func)
        }

        override fun <T : Closeable> closing(value: T): T {
            closable1 += value
            return value
        }

        override fun <T : AsyncCloseable> closing(value: T): T {
            closable2 += value
            return value
        }

        override suspend fun asyncClose() {
            jobs.forEach {
                try {
                    it.cancelAndJoin()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
            closable1.forEach {
                it.close()
            }
            closable2.forEach {
                it.asyncClose()
            }
        }
    }

    fun testing(func: suspend TestContext.() -> Unit): TestResult = runTest {
        MultiFixedSizeThreadNetworkDispatcher(4).use { networkManager ->
            withContext(networkManager + CoroutineName("MainTest")) {
                val textCtx = TestContextImpl(networkManager = networkManager)
                try {
                    func(textCtx)
                } finally {
                    var c = 0
                    textCtx.asyncClose()
                }
                coroutineContext.job.children.forEach {
                    println("Child job: ${it}")
                }
                check(coroutineContext.job.children.count() == 0) { "Not all child jobs was finished" }
            }
        }
    }
}
