package pw.binom.proxy

import kotlinx.coroutines.delay
import pw.binom.proxy.server.services.ServerControlService
import pw.binom.strong.inject
import pw.binom.testing.Testing
import pw.binom.url.toURL
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class GatewayConnectTest {
    @Test
    fun connectTest() =
        Testing.async(dispatchTimeoutMs = 30.minutes) {
            Context.TestUtils.context {
//                InternalLog.default = SysLogger
                wait { server.context.inject<ServerControlService>().service.isGatewayConnected }

//                delay(30.minutes)
                test("get url") {
//                    val resp = client.connect(method = "GET", uri = "https://yandex.ru".toURL())
//                        .getResponse()
//                    println("response GOT!")
//                    resp.asyncCloseAnyway()
                    val text = client.getText(url = "https://google.com")
                }
                delay(1.seconds)
            }
        }
}
