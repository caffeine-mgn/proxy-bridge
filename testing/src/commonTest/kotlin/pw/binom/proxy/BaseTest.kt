package pw.binom.proxy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

abstract class BaseTest {
    protected fun testing(func: suspend () -> Unit) = runTest {
        withContext(Dispatchers.Default) {
            func()
        }
    }
}