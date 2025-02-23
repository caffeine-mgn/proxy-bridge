package pw.binom

import kotlinx.coroutines.suspendCancellableCoroutine

suspend fun <T> executeCoroutineInThread(func: () -> T): T =
    suspendCancellableCoroutine { con ->
        val thread = Thread({
            con.resumeWith(runCatching(func))
        }, "Wrapping coroutine")
        con.invokeOnCancellation {
            thread.interrupt()
        }
        thread.start()
    }
