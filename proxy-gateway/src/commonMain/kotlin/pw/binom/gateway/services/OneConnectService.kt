package pw.binom.gateway.services

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pw.binom.Urls
import pw.binom.WebSocketProcessing
import pw.binom.atomic.AtomicBoolean
import pw.binom.gateway.properties.GatewayRuntimeProperties
import pw.binom.io.http.Headers
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.addHeader
import pw.binom.io.httpClient.connectWebSocket
import pw.binom.io.useAsync
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.network.NetworkManager
import pw.binom.services.VirtualChannelService
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.inject
import pw.binom.url.toURL

class OneConnectService {
    private val networkManager by inject<NetworkManager>()
    private val runtimeProperties by inject<GatewayRuntimeProperties>()
    private val httpClient by inject<HttpClient>()
    private val logger by Logger.ofThisOrGlobal
    private val virtualChannelService by inject<VirtualChannelService>()

    private var job: Job? = null
    private val closing = AtomicBoolean(false)

    init {
        BeanLifeCycle.afterInit {
            GlobalScope.launch(networkManager + CoroutineName("gateway-control-connect")) {
                while (!closing.getValue() && isActive) {
                    processing()
                }
            }
        }
        BeanLifeCycle.preDestroy {
            job?.cancelAndJoin()
        }
    }

    private suspend fun processing() {
        val url = "${runtimeProperties.url}${Urls.CONTROL}".toURL()
        try {
            val connection = httpClient.connectWebSocket(
                uri = url
            ).also {
                it.addHeader("X-trace", "ee12b3")
                it.addHeader(
                    Headers.USER_AGENT,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/102.0.5005.197 Safari/537.36"
                )
            }.start(bufferSize = runtimeProperties.bufferSize)

            WebSocketProcessing(
                connection = connection,
                income = virtualChannelService.income,
                outcome = virtualChannelService.outcome,
            ).useAsync {
                it.processing(networkManager)
            }
        } catch (e: Throwable) {
            logger.info(text = "Can't connect to $url. Retry in ${runtimeProperties.reconnectDelay}")
            delay(runtimeProperties.reconnectDelay)
            return
        }
    }
}
