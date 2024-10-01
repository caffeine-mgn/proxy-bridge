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
import pw.binom.proxy.ProxyClientWebSocket
import pw.binom.proxy.dto.ControlEventDto
import pw.binom.proxy.dto.ControlRequestDto
import pw.binom.gateway.properties.GatewayRuntimeProperties
import pw.binom.io.ClosedException
import pw.binom.logger.info
import pw.binom.network.NetworkManager
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.EventSystem
import pw.binom.strong.inject
import pw.binom.url.toURL
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalStdlibApi::class)
class ProxyClientService : ProxyClient {
    private val closing = AtomicBoolean(false)
    private val logger by Logger.ofThisOrGlobal
    private val runtimeProperties by inject<GatewayRuntimeProperties>()
    private val httpClient by inject<HttpClient>()
    private val networkManager by inject<NetworkManager>()
    private val lock = SpinLock()
    private val eventSystem by inject<EventSystem>()
    private var currentClient: ProxyClientWebSocket? = null

    init {
        BeanLifeCycle.preDestroy {
            closing.setValue(true)
            lock.synchronize {
                val current = currentClient
                currentClient = null
                current
            }?.asyncClose()
        }
        BeanLifeCycle.afterInit {
            val dispatcher = coroutineContext[CoroutineDispatcher]
            val interceptor = coroutineContext[ContinuationInterceptor]

            GlobalScope.launch(networkManager + CoroutineName("gateway-control-connect")) {
                while (isActive && !closing.getValue()) {
                    val url = "${runtimeProperties.url}${Urls.CONTROL}".toURL()
                    val connection =
                        try {
                            logger.info("Try connect to $url")
                            httpClient.connectWebSocket(
                                uri = url
                            ).start(bufferSize = runtimeProperties.bufferSize)
                        } catch (e: SocketConnectException) {
                            logger.info(text = "Can't connect to $url. Socket Closed. Retry in ${runtimeProperties.reconnectDelay}")
                            delay(runtimeProperties.reconnectDelay)
                            continue
                        } catch (e: Throwable) {
                            logger.info(text = "Can't connect to $url. Retry in ${runtimeProperties.reconnectDelay}")
                            delay(runtimeProperties.reconnectDelay)
                            continue
                        }
                    try {
                        logger.info("Success connected")
                        val client = ProxyClientWebSocket(connection.connection)
                        lock.synchronize {
                            currentClient = client
                        }
                        while (!closing.getValue() && isActive) {
                            logger.info("Reading cmd")
                            val cmd = client.receiveCommand()
                            logger.info("Cmd was read: $cmd. Try dispatch")
                            eventSystem.dispatch(cmd)
                            logger.info("Event dispatched")
                        }
                    } catch (e: ClosedException) {
                        logger.info(text = "Connection lost. Retry reconnect in ${runtimeProperties.reconnectDelay}")
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
