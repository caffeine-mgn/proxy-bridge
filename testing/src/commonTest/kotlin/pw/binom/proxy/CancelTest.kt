package pw.binom.proxy

import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import pw.binom.atomic.AtomicReference
import pw.binom.testing.Testing
import pw.binom.thread.Thread
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class CancelTest {

    suspend fun read() {
        try {
            delay(1.seconds)
            println("read OK!")
        } catch (e: Throwable) {
            println("Read function has error")
            throw e
        }
    }

    suspend fun <T> noCancel2(
//        ctx: CoroutineContext = EmptyCoroutineContext,
        func: suspend () -> T,
    ): T {
        val ctx = coroutineContext.minusKey(kotlinx.coroutines.Job.Key)
        return withContext(ctx) {
            func()
        }
    }

    suspend fun <T> nonCancellable(
        ctx: CoroutineContext? = null,
        onCancel: CompletionHandler? = null,
        func: suspend () -> T,
    ): T {
        val error = AtomicReference<Throwable?>(null)
        return try {
            suspendCancellableCoroutine<T> { cancellable ->
                if (onCancel != null) {
                    cancellable.invokeOnCancellation { er ->
                        try {
                            onCancel(er)
                        } catch (e: Throwable) {
                            error.setValue(e)
                        }
                    }
                }
                val ctx = ctx ?: cancellable.context.minusKey(Job.Key)
                func.startCoroutine(object : Continuation<T> {
                    override val context: CoroutineContext
                        get() = ctx

                    override fun resumeWith(result: Result<T>) {
                        cancellable.resumeWith(result)
                    }
                })
            }
        } catch (e: CancellationException) {
            val ex = error.getValue()
            if (ex != null) {
                throw ex
            }
            throw e
        }
    }

    @Test
    fun aaa() = Testing.async {
        val job = GlobalScope.launch {
            launch {
                try {
                    nonCancellable(onCancel = {
                        println("read was cancelled!")
                        throw RuntimeException("123123")
                    }) {
                        read()
                        read()
                        read()
                    }
                    suspendCancellableCoroutine {
                        println("Suspend")
                        it.invokeOnCancellation {
                            println("Cancelled! $it")
                        }
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
        println("job: $job")
        Thread.sleep(1000)
        job.cancelAndJoin()
    }

    @Test
    fun dddd() = Testing.async {
        val c = Channel<Int>()
        val produser = GlobalScope.launch {
            try {
                println("wait 1 sec.")
                delay(1.seconds)
                println("wait 1 sec.")
                delay(1.seconds)
                println("wait 1 sec.")
                delay(1.seconds)
                println("send #1")
                c.trySend(1)
                c.send(1)
                println("wait 1 sec.")
                delay(1.seconds)
                println("send #1")
                c.send(2)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        println("wait 0.5 sec")
        delay(0.5.seconds)
        println("Closing channel!")
        c.close()
        println("wait 5 seconds")
        delay(5.seconds)
    }
}
