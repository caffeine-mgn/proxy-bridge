package pw.binom.proxy

import pw.binom.io.socket.UnknownHostException
import pw.binom.gateway.properties.GatewayRuntimeProperties
import pw.binom.url.toURL
import kotlin.test.Test
import kotlin.test.fail

class UnknownHostTest : BaseTest() {
    @Test
    fun unknownHost() = testing {
        prepareNetwork(GatewayRuntimeProperties.TransportType.WS) { client ->
            try {
                client.connect(method = "GET", uri = "https://olololo/".toURL())
                    .getResponse()
                    .readText { it.readText() }
                fail("Should throw UnknownHostException1")
            } catch (e: UnknownHostException) {
                // Do nothing
            }
        }
    }

    @Test
    fun reconnectAfterUnknownHost() = testing {
        prepareNetwork(GatewayRuntimeProperties.TransportType.WS) { client ->
            try {
                client.connect(method = "GET", uri = "https://olololo/".toURL())
                    .getResponse()
                    .readText { it.readText() }
                fail("Should throw UnknownHostException")
            } catch (e: UnknownHostException) {
                // Do nothing
            }
            client.connect(method = "GET", uri = "https://www.google.com/".toURL())
                .getResponse()
                .readText { it.readText() }
        }
    }
}
