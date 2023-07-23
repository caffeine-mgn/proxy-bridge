package pw.binom.proxy

import kotlinx.coroutines.ExperimentalCoroutinesApi
import pw.binom.url.toURL
import kotlin.test.Ignore
import kotlin.test.Test
import pw.binom.proxy.client.RuntimeProperties as ClientRuntimeProperties

@OptIn(ExperimentalCoroutinesApi::class)
class ProtocolTest : BaseTest() {

    private suspend fun baseTest(transportType: ClientRuntimeProperties.TransportType) {
        prepareNetwork(transportType) { client ->
            val text = client.connect(method = "GET", uri = "https://www.google.com/".toURL())
                .getResponse()
                .readText {
                    it.readText()
                }
            println("text: $text")
        }
    }

    @Test
    fun testTcpOverHttp() = testing {
        baseTest(ClientRuntimeProperties.TransportType.TCP_OVER_HTTP)
    }

    @Test
    fun testWs() = testing {
        baseTest(ClientRuntimeProperties.TransportType.WS)
    }

    @Ignore
    @Test
    fun testTcpPool() = testing {
        baseTest(ClientRuntimeProperties.TransportType.HTTP_POOLING)
    }
}
