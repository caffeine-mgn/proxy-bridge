package pw.binom.proxy

import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import pw.binom.io.IOException
import pw.binom.io.useAsync
import pw.binom.gateway.properties.GatewayRuntimeProperties
import pw.binom.url.toURL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class ClientGoneTest : BaseTest() {
    @Test
    fun test() = testing {
        val port = Ports()
        port.instance(GatewayRuntimeProperties.TransportType.WS).useAsync {
            supervisorScope {
//            it.server.destroy()
                delay(1.seconds)
                it.node.destroy()
                println("----====Connection gone!!! WAIT 5 sec====----")
                it.node.awaitDestroy()
                delay(1.seconds)
                println("----====Connection gone!!! THEN TRY TO WORK====----")
                try {
                    it.client.connect(method = "GET", uri = "https://google.com".toURL())
                        .useAsync { it.getResponse().asyncClose() }
                } catch (e: IOException) {
                    assertEquals("Invalid response code: 503", e.message)
                }
            }
        }
    }
}
