package pw.binom.proxy

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import pw.binom.io.use
import pw.binom.network.MultiFixedSizeThreadNetworkDispatcher

abstract class BaseTest {
    protected fun testing(func: suspend () -> Unit) =
        runTest(dispatchTimeoutMs = 60_000) {
            MultiFixedSizeThreadNetworkDispatcher(5).use { networkManager ->
                withContext(networkManager) {
                    func()
                }
            }
        }
}
