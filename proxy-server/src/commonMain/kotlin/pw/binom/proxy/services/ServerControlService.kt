package pw.binom.proxy.services

/*
import kotlinx.coroutines.*
import pw.binom.*
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.date.DateTime
import pw.binom.exceptions.TimeoutException
import pw.binom.io.ClosedException
import pw.binom.io.EOFException
import pw.binom.io.socket.UnknownHostException
import pw.binom.io.useAsync
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.warn
import pw.binom.metric.MetricProvider
import pw.binom.metric.MetricProviderImpl
import pw.binom.metric.MetricUnit
import pw.binom.network.NetworkManager
import pw.binom.proxy.TransportChannelId
import pw.binom.proxy.channels.TransportChannel
import pw.binom.proxy.dto.ControlEventDto
import pw.binom.proxy.dto.ControlRequestDto
import pw.binom.proxy.dto.ChannelStateInfo
import pw.binom.proxy.properties.ProxyProperties
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.EventSystem
import pw.binom.strong.inject
import pw.binom.subchannel.TcpExchange
import pw.binom.subchannel.WorkerChanelClient
import pw.binom.uuid.nextUuid
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class ServerControlService : MetricProvider {

    interface ChannelState {
        val id: TransportChannelId
        val channel: FrameChannel
        val locked: SpinLock
        val lastTouch: DateTime
        val description: String?
    }

    private val metricProvider = MetricProviderImpl()
    override val metrics: List<MetricUnit> by metricProvider
    private val logger by Logger.ofThisOrGlobal
    private val proxyProperties by inject<ProxyProperties>()
    private val networkManager by inject<NetworkManager>()
    private val gatewayClientService by inject<GatewayClientService>()
    private val eventSystem by inject<EventSystem>()
    private val channelProvider by inject<ChannelProvider>()

    private val channelCount = metricProvider.gaugeLong(name = "channel_total") {
        channelsLock.synchronize {
            channels.size.toLong()
        }
    }

    private val activeChannelCount = metricProvider.gaugeLong(name = "channel_active") {
        channelsLock.synchronize {
            channels.count {
                it.value.locked.synchronize {
                    it.value.isBusy
                }
            }
        }.toLong()
    }
    private val timeoutCounter = metricProvider.counterLong("channel_timeout_counter")

    private var cleanUpBackgroundJob: Job? = null

    init {
        BeanLifeCycle.afterInit {
            cleanUpBackgroundJob =
                GlobalScope.launch(networkManager) {
                    while (isActive) {
                        delay(30.seconds)
                        val forRemove = ArrayList<ChannelWrapper>()
                        channelsLock.synchronize {
                            val it = channels.iterator()
                            while (it.hasNext()) {
                                val e = it.next()
                                e.value.locked.lock()
                                try {
                                    when {
                                        e.value.isBusy -> continue
                                        e.value.lastTouch + proxyProperties.channelIdleTime >= DateTime.now -> continue
                                    }
                                } finally {
                                    e.value.locked.unlock()
                                }
                                logger.info("Cleanup channel ${e.value.id}")
                                forRemove += e.value
                                it.remove()
                            }
                        }
                        if (forRemove.isEmpty()) {
                            continue
                        }
                        logger.info("Cleanup channels: ${forRemove.map { it.id }.joinToString()}")
                        forRemove.forEach {
                            gatewayClientService.sendCmd(
                                ControlRequestDto(
                                    closeChannel = ControlRequestDto.CloseChannel(it.id)
                                )
                            )
                        }
                        forRemove.forEach {
                            it.channel.asyncCloseAnyway()
                        }
                    }

                }
        }
    }

    /**
     * Buffer for write simple commands
     */
    private val channels = HashMap<TransportChannelId, ChannelWrapper>()
    private var channelIdIterator = 0
    private val proxyWater = HashMap<TransportChannelId, CancellableContinuation<Unit>>()
    private val channelWater = HashMap<TransportChannelId, ChannelWater>()

    private class ChannelWater(
        val con: CancellableContinuation<ChannelWrapper>,
        var channelId: TransportChannelId,
    ) {
        val startWait = DateTime.now
    }

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

    suspend fun forceClose() {
        channelsLock.synchronize {
            val set = channels.map { it.value.channel }
            channels.clear()
            set
        }.forEach {
            it.asyncCloseAnyway()
        }
    }

    fun getChannelsState() = channelsLock.synchronize {
        channels.map {
            ChannelStateInfo(
                id = it.key,
                behavior = it.value.locked.synchronize { it.value.description },
                output = it.value.channel.write,
                input = it.value.channel.read,
            )
        }
    }

    fun getChannels() = channelsLock.synchronize {
        ArrayList<ChannelState>(channels.values)
    }

    private class ChannelWrapper(channel: FrameChannel, override val id: TransportChannelId) : ChannelState {
        override val channel = FrameChannelWithCounter(channel)
        var isBusy: Boolean = false
        override val locked = SpinLock()
        override var lastTouch = DateTime.now
            private set
        override var description: String? = null

        fun touch() {
            lastTouch = DateTime.now
        }
    }

    private val eventListener by BeanLifeCycle.afterInit {
        eventSystem.listen(ControlEventDto::class) { event ->
            eventProcessing(event)
        }
    }

    init {
        BeanLifeCycle.preDestroy {
            eventListener.close()
            logger.info("Try to stop background job")
            cleanUpBackgroundJob?.cancelAndJoin()
            logger.info("Background success stopped")
        }
    }

    suspend fun connectProcessing(id: TransportChannelId, channel: VirtualChannelManager) {
        val water = channelWaterLock.synchronize { channelWater.remove(id) }
        if (water == null) {
            channel.asyncCloseAnyway()
            return
        }
        val wrapper = ChannelWrapper(id = id, channel = TODO())
        channelsLock.synchronize { channels[id] = wrapper }
        val context = coroutineContext
        water.con.resume(wrapper) { ex, value, ctx ->
            channelsLock.synchronize { channels.remove(id) }
            GlobalScope.launch(context + CoroutineName("Closing channel ${id}")) {
                channel.asyncCloseAnyway()
            }
        }
    }

    fun channelClosed(channelId: TransportChannelId) {
        channelsLock.synchronize { channels.remove(channelId) }
        channelWaterLock.synchronize { channelWater.remove(channelId) }?.con?.resumeWithException(ClosedException())
        proxyWaterLock.synchronize { proxyWater.remove(channelId) }?.resumeWithException(ClosedException())
    }

    private suspend fun getOrCreateIdleChannel(): ChannelWrapper {
        val existChannel =
            channelsLock.synchronize {
                channels.values.firstOrNull {
                    it.locked.synchronize {
                        !it.isBusy
                    }
                }
            }
        if (existChannel != null) {
            existChannel.touch()
            return existChannel
        }
//        val newChannelId = ChannelId(lockForIdIterator.synchronize { channelIdIterator++ })
        val newChannelId = TransportChannelId(Random.nextUuid().toShortString())
        gatewayClientService.sendCmd(
            ControlRequestDto(
                emmitChannel = ControlRequestDto.EmmitChannel(
                    id = newChannelId,
                    type = TransportType.WS_SPLIT,
                    bufferSize = PackageSize(proxyProperties.bufferSize - 2),
                )
            )
        )
        logger.info("Wait new channel $newChannelId...")
        val result = withTimeoutOrNull(10.seconds) {
            val e = suspendCancellableCoroutine {
                val water = ChannelWater(con = it, channelId = newChannelId)
                it.invokeOnCancellation {
                    channelWaterLock.synchronize { channelWater.remove(water.channelId) }
                }
                channelWaterLock.synchronize { channelWater[newChannelId] = water }
            }
            logger.info("Channel got $newChannelId")
            e
        }
        if (result == null) {
            timeoutCounter.inc()
            throw TimeoutException("Timeout waiting a channel")
        }
        return result
    }

    /**
     * Возвращает новый транспортный поток. В случае успеха помечает поток как занятый.
     */
    suspend fun connect(
        host: String,
        port: Int,
    ): TcpExchange {
        val channel = this.channelProvider.getNewChannel()
        try {
            return WorkerChanelClient(channel).useAsync { worker ->
                worker.startTcp(
                    host = host,
                    port = port,
                )
            }
        } catch (e: Throwable) {
            channel.asyncCloseAnyway()
            throw e
        }
        /*
        val channel = getOrCreateIdleChannel()
        gatewayClientService.sendCmd(
            ControlRequestDto(
                proxyConnect = ControlRequestDto.ProxyConnect(
                    id = channel.id,
                    host = host,
                    port = port,
                    compressLevel = compressLevel,
                )
            )
        )
        channel.locked.synchronize {
            channel.touch()
            channel.description = "$host:$port"
            channel.isBusy = true
        }
        try {
            logger.info("Wait until gateway connect to $host:$port...")
            logger.info("Gateway connected to $host:$port!")
            channel.isBusy = true
            return CloseWaterFrameChannel(channel.channel) {
                returnChannelToPool(channel)
            }//.withLogger("$host:$port")
        } catch (e: Throwable) {
            logger.info("Gateway can't connect to $host:$port!")
            channel.locked.synchronize {
                channel.isBusy = false
            }
            throw e
        }
        */
    }

    private suspend fun eventProcessing(eventDto: ControlEventDto) {
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
                            it.isBusy = false
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
                        channel.isBusy = false
                    }
                    returnChannelToPool(channel)
                }
            }

            eventDto.channelEmmitError != null -> {
                val channelId = eventDto.channelEmmitError!!.channelId
                val msg = eventDto.channelEmmitError!!.msg
                channelWaterLock.synchronize { channelWater.remove(channelId) }
                    ?.con
                    ?.resumeWithException(RuntimeException(msg ?: "Unknown error"))
            }
        }
    }

    private fun returnChannelToPool(channelWrapper: ChannelWrapper) {
        channelWrapper.locked.synchronize {
            channelWrapper.isBusy = false
            channelWrapper.description = null
            channelWrapper.touch()
        }
    }

    suspend fun sendToPool(channel: TransportChannel) {
        if (channel.isClosed) {
            channelsLock.synchronize { channels.remove(channel.id) }
            return
        }
        val channelWrapper = channelsLock.synchronize { channels[channel.id] }
        if (channelWrapper == null) {
            channel.asyncClose()
            return
        }
        returnChannelToPool(channelWrapper)
    }
}
*/
