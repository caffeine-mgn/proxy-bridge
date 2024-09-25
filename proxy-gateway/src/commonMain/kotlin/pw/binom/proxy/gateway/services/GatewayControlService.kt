package pw.binom.proxy.gateway.services

import kotlinx.coroutines.*
import pw.binom.*
import pw.binom.atomic.AtomicBoolean
import pw.binom.io.ByteBuffer
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketClosedException
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.connectWebSocket
import pw.binom.io.socket.UnknownHostException
import pw.binom.io.useAsync
import pw.binom.io.writeByteArray
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.network.SocketConnectException
import pw.binom.proxy.Dto
import pw.binom.proxy.Urls
import pw.binom.proxy.gateway.properties.RuntimeProperties
import pw.binom.proxy.dto.ControlEventDto
import pw.binom.proxy.dto.ControlRequestDto
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.inject
import pw.binom.url.toURL
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalStdlibApi::class)
class GatewayControlService {
    private val runtimeProperties by inject<RuntimeProperties>()
    private val httpClient by inject<HttpClient>()
    private val channelService by inject<ChannelService>()
    private var currentConnection: WebSocketConnection? = null
    private val buffer1 = ByteBuffer(DEFAULT_BUFFER_SIZE)
    private val buffer2 = ByteBuffer(DEFAULT_BUFFER_SIZE)
    private val logger by Logger.ofThisOrGlobal
    private val closing = AtomicBoolean(false)

    init {
        BeanLifeCycle.preDestroy {
            closing.setValue(true)
            currentConnection?.asyncClose()
            buffer1.closeAnyway()
            buffer2.closeAnyway()
        }

        BeanLifeCycle.afterInit {
            val dispatcher = coroutineContext[CoroutineDispatcher]
            val interceptor = coroutineContext[ContinuationInterceptor]
            GlobalScope.launch(/*dispatcher!! + interceptor!! + */CoroutineName("gateway-control-connect")) {
                while (isActive && !closing.getValue()) {
                    try {
                        connectProcessing()
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        throw e
                    }
                }
            }
        }
    }

    private suspend fun connectProcessing() {
        val url = "${runtimeProperties.url}${Urls.CONTROL}".toURL()
        logger.info("Connect to $url....")
        val connection =
            try {
                httpClient.connectWebSocket(
                    uri = url
                ).start(bufferSize = runtimeProperties.bufferSize)
            } catch (e: SocketConnectException) {
                return
            }
        println("Connected to $url!")
        currentConnection = connection.connection
        coroutineScope {
            while (isActive && !closing.getValue()) {
                val cmd = try {
                    connection.connection.read().useAsync {
                        val len = it.readInt(buffer1)
                        val data = it.readByteArray(len, buffer1)
                        Dto.decode(ControlRequestDto.serializer(), data)
                    }
                } catch (e: WebSocketClosedException) {
                    continue
                }
                commandProcessing(cmd)
            }
        }
    }

    private suspend fun sendEvent(event: ControlEventDto) {
        try {
            logger.info("Send event $event")
            val data = Dto.encode(ControlEventDto.serializer(), event)
            val connect = currentConnection ?: TODO()
            connect.write(MessageType.BINARY).useAsync {
                it.writeInt(data.size, buffer2)
                it.writeByteArray(data, buffer2)
            }
        } catch (e: Throwable) {
            throw RuntimeException("Can't send event $event", e)
        }
    }

    private suspend fun commandProcessing(cmd: ControlRequestDto) {
        when {
            cmd.emmitChannel != null -> {
                val channelId = cmd.emmitChannel!!.id
                val type = cmd.emmitChannel!!.type
                try {
                    channelService.createChannel(channelId = channelId, type = type)
                } catch (e: Throwable) {
                    sendEvent(
                        ControlEventDto(
                            channelEmmitError = ControlEventDto.ChannelEmmitError(
                                channelId = channelId,
                                msg = e.message ?: "Can't open channel"
                            )
                        )
                    )
                }
            }

            cmd.proxyConnect != null -> {
                val channelId = cmd.proxyConnect!!.id
                val host = cmd.proxyConnect!!.host
                val port = cmd.proxyConnect!!.port
                try {
                    val job = channelService.connect(
                        channelId = channelId,
                        host = host,
                        port = port,
                    )
                    sendEvent(
                        ControlEventDto(
                            proxyConnected = ControlEventDto.ProxyConnected(
                                channelId = channelId,
                            )
                        )
                    )
                    GlobalScope.launch {
                        try {
                            job.start()
                        } finally {
                            sendEvent(
                                ControlEventDto(
                                    chanelEof = ControlEventDto.ChanelEof(
                                        channelId = channelId,
                                    )
                                )
                            )
                        }
                    }
                } catch (e: UnknownHostException) {
                    sendEvent(
                        ControlEventDto(
                            proxyError = ControlEventDto.ProxyError(
                                channelId = channelId,
                                msg = null
                            )
                        )
                    )
                } catch (e: Throwable) {
                    sendEvent(
                        ControlEventDto(
                            proxyError = ControlEventDto.ProxyError(
                                channelId = channelId,
                                msg = e.message ?: "Unknown error"
                            )
                        )
                    )
                }
            }

            cmd.resetChannel != null -> {
                val channelId = cmd.resetChannel!!.id
                channelService.reset(channelId = channelId)
            }
        }
    }
}
