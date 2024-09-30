package pw.binom.proxy

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Contextual
import pw.binom.io.httpClient.addHeader
import pw.binom.io.socket.InetSocketAddress
import pw.binom.io.use
import pw.binom.io.useAsync
import pw.binom.network.MultiFixedSizeThreadNetworkDispatcher
import pw.binom.proxy.server.services.ServerControlService
import pw.binom.strong.inject
import pw.binom.testing.Testing
import pw.binom.testing.shouldBeTrue
import pw.binom.url.toURL
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class GatewayConnectTest {
    @Test
    fun connectTest() = Testing.async(dispatchTimeoutMs = 30.minutes) {
        Context.TestUtils.context {
//                InternalLog.default = SysLogger
            wait { server.context.inject<ServerControlService>().service.isGatewayConnected }
            test("get url") {
                server.controlService.getChannels().isEmpty().shouldBeTrue()
                println("Try to get again")
                client.connect(method = "GET", uri = "https://vk.com".toURL()).addHeader("User-Agent", "curl/8.9.1")
                    .addHeader("Accept", "*/*").getResponse().useAsync { response ->
                        println("responseCode: ${response.responseCode}")
                        println("Headers: ${response.inputHeaders}")
                        val txt = response.readText { it.readText() }
                        println("response: $txt")
                    }
//                client.getText(url = "https://yandex.com")
                println("Message got!")
                delay(1.seconds)
                server.controlService.getChannels().forEach {
                    println("1 Channel: ${it.role} ${it.channel}")
                }
                repeat(3) {
                    println("------------------------")
                }
                println("Try to get text again")
                val v = client.getText(url = "https://yandex.ru")
                println("Message got! $v")
                delay(1.seconds)
                server.controlService.getChannels().forEach {
                    println("2 Channel: ${it.role} ${it.channel}")
                }
            }
            server
            delay(1.seconds)
        }
    }

    @Test
    fun closeNowTest() {
        Testing.async(dispatchTimeoutMs = 10.seconds) {
            Context.TestUtils.context {
                MultiFixedSizeThreadNetworkDispatcher(4).use { nd ->
                    var port: Int = 0

                    GlobalScope.launch {
                        nd.bindTcp(InetSocketAddress.resolve(host = "127.0.0.1", port = 0)).use { server ->
                            println("Client connected")
                            port = server.port
                            server.accept().closeAnyway()
                        }
                    }
                    wait { port != 0 }
                    wait { server.context.inject<ServerControlService>().service.isGatewayConnected }

                    try {
                        client.getText(method = "GET", "https://127.0.0.1:$port")
                    } catch (e:Throwable){
                        println("ERROROROR on getText")
                    }
                }
//                InternalLog.default = SysLogger
            }
        }
        // TODO написать тест когда https сервер сразу после открытия соединения закрывает его
    }
}
