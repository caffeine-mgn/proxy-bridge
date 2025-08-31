package pw.binom

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class JobTesdt {
    @Test
    fun test() = runTest {
        withContext(Dispatchers.Default) {
            println("Job->${coroutineContext[Job]}")
            GlobalScope.launch(coroutineContext) {
                delay(4.seconds)
                cancel()
            }
            GlobalScope.launch(coroutineContext) {
                try {
                    delay(3.seconds)
                    println("#1 done")
                } catch (e: CancellationException) {
                    println("#1 canceled")
                }
            }
            GlobalScope.launch(coroutineContext) {
                try {
                    delay(5.seconds)
                    println("#2 done")
                } catch (e: CancellationException) {
                    println("#2 canceled")
                }

            }
            delay(4.seconds)
            cancel()
        }
    }
}
