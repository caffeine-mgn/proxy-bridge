package pw.binom.gateway.services

import kotlinx.coroutines.*
import pw.binom.*
import pw.binom.atomic.AtomicBoolean
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.connectWebSocket
import pw.binom.logger.Logger
import pw.binom.network.SocketConnectException
import pw.binom.proxy.ProxyClient
import pw.binom.proxy.dto.ControlEventDto
import pw.binom.proxy.dto.ControlRequestDto
import pw.binom.gateway.properties.GatewayRuntimeProperties
import pw.binom.io.ClosedException
import pw.binom.io.http.Headers
import pw.binom.io.httpClient.addHeader
import pw.binom.io.useAsync
import pw.binom.logger.info
import pw.binom.logger.infoSync
import pw.binom.network.NetworkManager
import pw.binom.properties.PingProperties
import pw.binom.proxy.TransportChannelId
import pw.binom.proxy.FrameProxyClient
import pw.binom.services.VirtualChannelService
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.EventSystem
import pw.binom.strong.inject
import pw.binom.url.toURL
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalStdlibApi::class)
@Deprecated(message = "Not use it")
class ProxyClientService : ProxyClient {
    private val closing = AtomicBoolean(false)
    private val logger by Logger.ofThisOrGlobal
    private val runtimeProperties by inject<GatewayRuntimeProperties>()
    private val httpClient by inject<HttpClient>()
    private val networkManager by inject<NetworkManager>()
    private val pingProperties by inject<PingProperties>()
    private val lock = SpinLock()
    private val eventSystem by inject<EventSystem>()
    private var currentClient: ProxyClient? = null
    private val virtualChannelService by inject<VirtualChannelService>()

    init {
        BeanLifeCycle.preDestroy {
            closing.setValue(true)
            lock.synchronize {
                val current = currentClient
                currentClient = null
                current
            }?.asyncClose()
        }
        logger.infoSync("Created")
        BeanLifeCycle.afterInit {
//            val dispatcher = coroutineContext[CoroutineDispatcher]
//            val interceptor = coroutineContext[ContinuationInterceptor]
            logger.info("Starting control...")
            GlobalScope.launch(networkManager + CoroutineName("gateway-control-connect")) {
                while (isActive && !closing.getValue()) {
                    val url = "${runtimeProperties.url}${Urls.CONTROL}".toURL()
                    val connection =
                        try {
                            logger.info("Try connect to $url")
                            httpClient.connectWebSocket(
                                uri = url
                            ).also {
                                it.addHeader("X-trace", "ee12b3")
                                it.addHeader(
                                    Headers.USER_AGENT,
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/102.0.5005.197 Safari/537.36"
                                )
                            }.start(bufferSize = runtimeProperties.bufferSize)
                        } catch (e: SocketConnectException) {
                            logger.info(text = "Can't connect to $url. $e in ${runtimeProperties.reconnectDelay}")
                            delay(runtimeProperties.reconnectDelay)
                            continue
                        } catch (e: Throwable) {
                            logger.info(text = "Can't connect to $url. Retry in ${runtimeProperties.reconnectDelay}")
                            delay(runtimeProperties.reconnectDelay)
                            continue
                        }
                    try {
                        logger.info("Success connected. Starting processing")
                        WebSocketProcessing(
                            connection = connection,
                            income = virtualChannelService.income,
                            outcome = virtualChannelService.outcome,
                            pingProperties = pingProperties,
                        ).useAsync {
                            it.processing(networkManager)
                        }


                        val client = FrameProxyClient(
                            WsFrameChannel(
                                con = connection.connection,
                                channelId = TransportChannelId("CONTROL"),
                            )
                        )
                        lock.synchronize {
                            currentClient = client
                        }
                        while (!closing.getValue() && isActive) {
                            val cmd = client.receiveCommand()
                            eventSystem.dispatch(cmd)
                        }
                    } catch (e: ClosedException) {
                        currentClient?.asyncCloseAnyway()
                        logger.info(text = "Connection lost. Retry reconnect in ${runtimeProperties.reconnectDelay}")
                        e.printStackTrace()
                        delay(runtimeProperties.reconnectDelay)
                    } catch (e: Throwable) {
                        logger.info(text = "Can't receive command from server", exception = e)
                    }
                }
            }
        }
    }

    private fun getClient() =
        lock.synchronize { currentClient } ?: throw IllegalStateException("No connection to proxy")

    override suspend fun sendEvent(event: ControlEventDto) {
        getClient().sendEvent(event)
    }

    override suspend fun receiveCommand(): ControlRequestDto =
        getClient().receiveCommand()

    override suspend fun asyncClose() {
    }
}
