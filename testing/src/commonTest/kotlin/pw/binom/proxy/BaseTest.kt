package pw.binom.proxy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import pw.binom.network.Network

abstract class BaseTest {
    protected fun testing(func: suspend () -> Unit) = runTest(dispatchTimeoutMs = 60_000) {
        withContext(Dispatchers.Network) {
            func()
        }
    }
}
