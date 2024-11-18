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
import pw.binom.logger.warn
import pw.binom.network.NetworkManager
import pw.binom.properties.PingProperties
import pw.binom.services.VirtualChannelService
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.inject
import pw.binom.url.toURL

class OneConnectService {
    private val networkManager by inject<NetworkManager>()
    private val runtimeProperties by inject<GatewayRuntimeProperties>()
    private val httpClient by inject<HttpClient>()
    private val pingProperties by inject<PingProperties>()
    private val logger by Logger.ofThisOrGlobal
    private val virtualChannelService by inject<VirtualChannelService>()

    private var jobs = emptyList<Job>()
    private val closing = AtomicBoolean(false)

    init {
        BeanLifeCycle.afterInit {
            jobs = (0 until runtimeProperties.connectCount).map {
                GlobalScope.launch(networkManager + CoroutineName("gateway-control-connect")) {
                    try {
                        while (!closing.getValue() && isActive) {
                            processing()
                        }
                    } catch (e: Throwable) {
                        logger.warn(text = "Missing connect to server", exception = e)
                        delay(runtimeProperties.reconnectDelay)
                    }
                }
            }
        }
        BeanLifeCycle.preDestroy {
            jobs.forEach {
                it.cancelAndJoin()
            }
        }
    }

    private suspend fun processing() {
        val url = "${runtimeProperties.url}${Urls.CONTROL}".toURL()
        logger.info("Try to connect!")
        val connection = try {
            httpClient.connectWebSocket(
                uri = url
            ).also {
                it.addHeader("X-trace", "ee12b3")
                it.addHeader(
                    Headers.USER_AGENT,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/102.0.5005.197 Safari/537.36"
                )
            }.start(bufferSize = runtimeProperties.bufferSize)
        } catch (e: Throwable) {
            logger.info(text = "Can't connect to $url. Retry in ${runtimeProperties.reconnectDelay}", exception = e)
            return
        }
        logger.info("Connected to $url success")
        try {
            WebSocketProcessing(
                connection = connection,
                income = virtualChannelService.income,
                outcome = virtualChannelService.outcome,
                pingProperties = pingProperties,
            ).useAsync {
                try {
                    logger.info("Starting WebSocket processing")
                    it.processing(networkManager)
                } catch (e: Throwable) {
                    logger.info(text = "Error during processing", exception = e)
                } finally {
                    logger.info("WebSocket processing finished!")
                }
            }
        } catch (e: Throwable) {
            logger.info(text = "Error during close", exception = e)
        }
    }
}
