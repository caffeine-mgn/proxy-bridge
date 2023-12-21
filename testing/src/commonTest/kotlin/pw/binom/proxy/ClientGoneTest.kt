package pw.binom.proxy

import kotlinx.coroutines.test.runTest
import pw.binom.io.use
import pw.binom.proxy.client.RuntimeProperties
import pw.binom.url.toURL
import kotlin.test.Test

class ClientGoneTest : BaseTest() {
    @Test
    fun test() = testing {
        prepareNetwork(RuntimeProperties.TransportType.WS) {

        }
        val port = Ports()
        port.instance(RuntimeProperties.TransportType.WS).use {
//            it.server.destroy()
            it.node.destroy()
            try {
                it.client.connect(method = "GET", uri = "https://google.com".toURL()).getResponse()
                    .readText {
                        it.readText()
                    }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}
