package pw.binom.proxy

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import pw.binom.io.use
import pw.binom.logger.Logger
import pw.binom.logger.WARNING
import pw.binom.proxy.client.RuntimeProperties
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class ReconnectTest : BaseTest() {
    @Test
    fun unknownHost() = testing {
        prepareNetwork(RuntimeProperties.TransportType.WS) { client ->
        }
    }

    @Test
    fun nodeReconnect() = runTest {
        Logger.getLogger("Strong.Starter").level = Logger.WARNING
        val ports = Ports()
        ports.prepareNetworkDispatcher().use { nd ->
            var server = ports.createServer(nd)
            delay(1.seconds)
            val node = ports.createNode(
                nd = nd,
                transportType = RuntimeProperties.TransportType.WS,
                config = { it.copy(reconnectTimeout = 1.seconds) })
            delay(2.seconds)
            val http = ports.createHttpClient(nd)
            http.checkIsOk()

            // -----
            println("---------------STOP NODE---------------")
            server.destroy()
            server = ports.createServer(nd)
            delay(1.seconds)
            http.checkIsOk()

            http.closeAnyway()
            node.destroy()
            server.destroy()
        }
    }
}
