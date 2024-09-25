package pw.binom.proxy.server.services

import kotlinx.coroutines.*
import pw.binom.*
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.date.DateTime
import pw.binom.io.*
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketClosedException
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.io.socket.UnknownHostException
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.infoSync
import pw.binom.proxy.*
import pw.binom.proxy.channels.TransportChannel
import pw.binom.proxy.dto.ControlEventDto
import pw.binom.proxy.dto.ControlRequestDto
import pw.binom.proxy.server.exceptions.ClientMissingException
import pw.binom.proxy.server.properties.RuntimeClientProperties
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.inject
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

class ServerControlService {
    private val logger by Logger.ofThisOrGlobal
    private val runtimeClientProperties by inject<RuntimeClientProperties>()
    private var lastConnection: WebSocketConnection? = null

    private var job: Job? = null

    init {
        BeanLifeCycle.afterInit {
            job =
                GlobalScope.launch {
                    while (isActive) {
                        delay(5.seconds)
                        val forRemove = ArrayList<TransportChannel>()
                        channelsLock.synchronize {
                            val it = channels.iterator()
                            while (it.hasNext()) {
                                val e = it.next()
                                if (e.value.lastTouch + runtimeClientProperties.channelIdleTime < DateTime.now) {
                                    forRemove += e.value.channel
                                    it.remove()
                                }
                            }
                        }
                        forRemove.forEach {
                            it.asyncCloseAnyway()
                        }
                    }

                }
        }
        BeanLifeCycle.preDestroy {
            job?.cancelAndJoin()
        }
    }

    /**
     * Buffer for write simple commands
     */
    private val buffer = ByteBuffer(1024 * 2)
    private val channels = HashMap<ChannelId, ChannelWrapper>()
    private var channelIdIterator = 0
    private val proxyWater = HashMap<ChannelId, CancellableContinuation<Unit>>()
    private val channelWater = HashMap<ChannelId, CancellableContinuation<ChannelWrapper>>()

    /**
     * Lock for [channelIdIterator]
     */
    private val lockForIdIterator = SpinLock()

    /**
     * Lock for [proxyWater]
     */
    private val proxyWaterLock = SpinLock()

    /**
     * Lock for [channelWater]
     */
    private val channelWaterLock = SpinLock()

    /**
     * Lock for [channels]
     */
    private val channelsLock = SpinLock()

    private class ChannelWrapper(val channel: TransportChannel) {
        var role: ChannelRole = ChannelRole.IDLE
        var changing: Boolean = false
        val locked = SpinLock()
        var lastTouch = DateTime.now
            private set

        fun touch() {
            lastTouch = DateTime.now
        }
    }

    val isGatewayConnected
        get() = lastConnection != null

    init {
        BeanLifeCycle.preDestroy {
            buffer.close()
            lastConnection?.asyncCloseAnyway()
            lastConnection = null
        }
    }

    suspend fun controlProcessing(connection: WebSocketConnection) {
        lastConnection?.asyncCloseAnyway()
        lastConnection = connection
        logger.info("Start processing... lastConnection is set")
        try {
            while (true) {
                val event = try {
                    connection.read().useAsync {
                        val packageSize = it.readInt(buffer)
                        val data = it.readBytes(packageSize)
                        Dto.decode(ControlEventDto.serializer(), data)
                    }
                } catch (e: WebSocketClosedException) {
                    logger.info("Processing finished")
                    break
                }
                eventProcessing(event)
            }
        } catch (e: Throwable) {
            logger.info("Error on control processing")
            throw e
        }
    }

    suspend fun newChannel(channel: TransportChannel) {
        val water = channelWaterLock.synchronize { channelWater.remove(channel.id) }
        if (water == null) {
            channel.asyncCloseAnyway()
            return
        }
        val wrapper = ChannelWrapper(channel)
        channelsLock.synchronize { channels[channel.id] = wrapper }
        val context = coroutineContext
        water.resume(wrapper) { ex, value, ctx ->
            channelsLock.synchronize { channels.remove(channel.id) }
            GlobalScope.launch(context + CoroutineName("Closing channel ${channel.id}")) {
                channel.asyncCloseAnyway()
            }
        }
    }

    fun channelClosed(channelId: ChannelId) {
        channelsLock.synchronize { channels.remove(channelId) }
        channelWaterLock.synchronize { channelWater.remove(channelId) }?.resumeWithException(ClosedException())
        proxyWaterLock.synchronize { proxyWater.remove(channelId) }?.resumeWithException(ClosedException())
    }

    private suspend fun getOrCreateIdleChannel(): ChannelWrapper {
        val existChannel =
            channelsLock.synchronize {
                channels.values.find {
                    it.locked.synchronize {
                        it.role == ChannelRole.IDLE && !it.changing
                    }
                }
            }
        if (existChannel != null) {
            existChannel.touch()
            return existChannel
        }
        val newChannelId = ChannelId(lockForIdIterator.synchronize { channelIdIterator++ })
        send(
            ControlRequestDto(
                emmitChannel = ControlRequestDto.EmmitChannel(
                    id = newChannelId,
                    type = TransportType.WS_SPLIT,
                )
            )
        )
        logger.info("Wait new channel $newChannelId...")
        return withTimeoutOrNull(10.seconds) {
            val e = suspendCancellableCoroutine {
                it.invokeOnCancellation {
                    channelWaterLock.synchronize { channelWater.remove(newChannelId) }
                }
                channelWaterLock.synchronize { channelWater[newChannelId] = it }
            }
            logger.info("Channel got $newChannelId")
            e
        } ?: throw RuntimeException("Timeout waiting a channel")
    }

    suspend fun connect(host: String, port: Short): TransportChannel {
        val channel = getOrCreateIdleChannel()
        send(
            ControlRequestDto(
                proxyConnect = ControlRequestDto.ProxyConnect(
                    id = channel.channel.id,
                    host = host,
                    port = port,
                )
            )
        )
        channel.locked.lock()
        channel.touch()
        channel.changing = true
        try {
            logger.info("Wait until gateway connect to $host:$port...")
            suspendCancellableCoroutine<Unit> {
                it.invokeOnCancellation {
                    channel.locked.synchronize {
                        channel.changing = false
                        proxyWaterLock.synchronize { proxyWater.remove(channel.channel.id) }
                    }
                }
                proxyWaterLock.synchronize { proxyWater[channel.channel.id] = it }
            }
            logger.info("Gateway connected to $host:$port!")
            channel.role = ChannelRole.PROXY
            channel.locked.unlock()
            return channel.channel
        } catch (e: Throwable) {
            logger.info("Gateway can't connect to $host:$port!")
            channel.locked.synchronize {
                channel.changing = false
            }
            throw e
        }
    }

    private suspend fun send(request: ControlRequestDto) {
        try {
            val data = Dto.encode(
                ControlRequestDto.serializer(),
                request
            )
            val connection = lastConnection ?: throw ClientMissingException()
            connection
                .write(MessageType.BINARY).useAsync {
                    it.writeInt(data.size, buffer)
                    it.writeByteArray(data, buffer)
                }
        } catch (e: Throwable) {
            throw RuntimeException("Can't send cmd $request")
        }
    }

    private fun eventProcessing(eventDto: ControlEventDto) {
        logger.infoSync("Income event $eventDto")
        when {
            eventDto.proxyConnected != null -> {
                val channelId = eventDto.proxyConnected!!.channelId
                proxyWaterLock.synchronize { proxyWater.remove(channelId) }?.resume(Unit)
            }

            eventDto.proxyError != null -> {
                val channelId = eventDto.proxyError!!.channelId
                val msg = eventDto.proxyError!!.msg
                proxyWaterLock.synchronize { proxyWater.remove(channelId) }
                    ?.resumeWithException(msg?.let { RuntimeException(it) } ?: UnknownHostException())
                channelsLock.synchronize { channels[channelId] }
                    ?.let {
                        it.locked.synchronize {
                            it.role = ChannelRole.IDLE
                            it.changing = false
                        }
                    }
            }

            eventDto.chanelEof != null -> {
                val channelId = eventDto.chanelEof!!.channelId
                val channel = channels[channelId]
                if (channel != null) {
                    proxyWaterLock.synchronize { proxyWater.remove(channelId) }
                        ?.resumeWithException(EOFException())
                    channel.locked.synchronize {
                        channel.role = ChannelRole.IDLE
                        channel.changing = false
                    }
                    channel.channel.breakCurrentRole()
                }
            }

            eventDto.channelEmmitError != null -> {
                val channelId = eventDto.channelEmmitError!!.channelId
                val msg = eventDto.channelEmmitError!!.msg
                channelWaterLock.synchronize { channelWater.remove(channelId) }
                    ?.resumeWithException(RuntimeException(msg ?: "Unknown error"))
            }
        }
    }

    suspend fun sendToPool(channel: TransportChannel) {
        val channelWrapper = channelsLock.synchronize { channels[channel.id] }
        if (channelWrapper == null) {
            channel.asyncClose()
            return
        }
        channel.breakCurrentRole()
        send(ControlRequestDto(resetChannel = ControlRequestDto.ResetChannel(channel.id)))
        channelWrapper.locked.synchronize {
            channelWrapper.role = ChannelRole.IDLE
            channelWrapper.changing = false
        }
    }
}
