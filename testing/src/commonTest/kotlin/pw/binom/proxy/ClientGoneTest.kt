package pw.binom.proxy

import pw.binom.io.IOException
import pw.binom.io.use
import pw.binom.proxy.client.RuntimeProperties
import pw.binom.url.toURL
import kotlin.test.Test
import kotlin.test.assertEquals

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
                it.client.connect(method = "GET", uri = "https://google.com".toURL())
                    .use { it.getResponse().asyncClose() }
            } catch (e: IOException) {
                assertEquals("Invalid response code: 503", e.message)
            }
        }
    }
}
