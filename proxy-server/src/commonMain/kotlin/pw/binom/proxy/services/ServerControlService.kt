package pw.binom.proxy.services

import kotlinx.coroutines.*
import pw.binom.*
import pw.binom.atomic.AtomicReference
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.date.DateTime
import pw.binom.exceptions.TimeoutException
import pw.binom.io.ClosedException
import pw.binom.io.EOFException
import pw.binom.io.socket.UnknownHostException
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.infoSync
import pw.binom.metric.MetricProvider
import pw.binom.metric.MetricProviderImpl
import pw.binom.metric.MetricUnit
import pw.binom.network.NetworkManager
import pw.binom.proxy.ChannelId
import pw.binom.proxy.channels.TransportChannel
import pw.binom.proxy.dto.ControlEventDto
import pw.binom.proxy.dto.ControlRequestDto
import pw.binom.proxy.dto.ChannelStateInfo
import pw.binom.proxy.properties.ProxyProperties
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.EventSystem
import pw.binom.strong.inject
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

class ServerControlService : MetricProvider {

    interface ChannelState {
        val channel: TransportChannel
        var behavior: Behavior?
        val locked: SpinLock
        val lastTouch: DateTime
    }

    private val metricProvider = MetricProviderImpl()
    override val metrics: List<MetricUnit> by metricProvider
    private val logger by Logger.ofThisOrGlobal
    private val proxyProperties by inject<ProxyProperties>()
    private val networkManager by inject<NetworkManager>()
    private val gatewayClientService by inject<GatewayClientService>()
    private val eventSystem by inject<EventSystem>()
    private var lastLock2 by AtomicReference<String?>(null)
    private var lastLock3 by AtomicReference<String?>(null)

    private inline fun SpinLock.lock(name: String) {
        if (!lock(10.seconds)) {
            logger.infoSync("Can't lock $name")
            lock()
        }
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun <T> SpinLock.synchronize2(func: () -> T): T {
        contract {
            callsInPlace(func, InvocationKind.EXACTLY_ONCE)
        }
        try {
            val s = Throwable().stackTraceToString()
            if (!lock(10.seconds)) {
                logger.infoSync("111Error! Can't lock. Already locked")
                logger.infoSync("111Current:\n$s")
                logger.infoSync("111Lock:\n$lastLock2")
                lock()
            }
            lastLock2 = s
            return func()
        } finally {
            lastLock2 = null
            unlock()
        }
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun <T> SpinLock.synchronize3(func: () -> T): T {
        contract {
            callsInPlace(func, InvocationKind.EXACTLY_ONCE)
        }
        try {
            val s = Throwable().stackTraceToString()
            if (!lock(10.seconds)) {
                logger.infoSync("222Error! Can't lock. Already locked")
                logger.infoSync("222Current:\n$s")
                logger.infoSync("222Lock:\n$lastLock3")
                lock()
            }
            lastLock3 = s
            return func()
        } finally {
            lastLock3 = null
            unlock()
        }
    }

    private val channelCount = metricProvider.gaugeLong(name = "channel_total") {
        channelsLock.synchronize2 {
            channels.size.toLong()
        }
    }
    private val activeChannelCount = metricProvider.gaugeLong(name = "channel_active") {
        channelsLock.synchronize2 {
            channels.count {
                it.value.locked.synchronize3 {
                    it.value.behavior != null
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
                        val forRemove = ArrayList<TransportChannel>()
                        channelsLock.synchronize2 {
                            val it = channels.iterator()
                            while (it.hasNext()) {
                                val e = it.next()
                                e.value.locked.lock()
                                try {
                                    when {
                                        e.value.isBusy -> continue
                                        e.value.behavior != null -> continue
                                        e.value.lastTouch + proxyProperties.channelIdleTime >= DateTime.now -> continue
                                    }
                                } finally {
                                    e.value.locked.unlock()
                                }
                                logger.info("Cleanup channel ${e.value.channel.id}")
                                forRemove += e.value.channel
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
                            it.asyncCloseAnyway()
                        }
                    }

                }
        }
    }

    /**
     * Buffer for write simple commands
     */
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

    suspend fun forceClose() {
        channelsLock.synchronize2 {
            val set = channels.map { it.value.channel }
            channels.clear()
            set
        }.forEach {
            it.asyncCloseAnyway()
        }
    }

    fun getChannelsState() = channelsLock.synchronize2 {
        channels.map {
            ChannelStateInfo(
                id = it.key,
                behavior = it.value.locked.synchronize3 { it.value.behavior?.description },
            )
        }
    }

    fun getChannels() = channelsLock.synchronize2 {
        ArrayList<ChannelState>(channels.values)
    }

    private class ChannelWrapper(override val channel: TransportChannel) : ChannelState {
        var isBusy: Boolean = false
        override var behavior: Behavior? = null
        override val locked = SpinLock()
        override var lastTouch = DateTime.now
            private set

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

    suspend fun newChannel(channel: TransportChannel) {
        val water = channelWaterLock.synchronize { channelWater.remove(channel.id) }
        if (water == null) {
            channel.asyncCloseAnyway()
            return
        }
        val wrapper = ChannelWrapper(channel)
        channelsLock.synchronize2 { channels[channel.id] = wrapper }
        val context = coroutineContext
        water.resume(wrapper) { ex, value, ctx ->
            channelsLock.synchronize2 { channels.remove(channel.id) }
            GlobalScope.launch(context + CoroutineName("Closing channel ${channel.id}")) {
                channel.asyncCloseAnyway()
            }
        }
    }

    fun channelClosed(channelId: ChannelId) {
        channelsLock.synchronize2 { channels.remove(channelId) }
        channelWaterLock.synchronize { channelWater.remove(channelId) }?.resumeWithException(ClosedException())
        proxyWaterLock.synchronize { proxyWater.remove(channelId) }?.resumeWithException(ClosedException())
    }

    private suspend fun getOrCreateIdleChannel(): ChannelWrapper {
        val existChannel =
            channelsLock.synchronize2 {
                channels.values.firstOrNull {
                    it.locked.synchronize3 {
                        it.behavior == null && !it.isBusy
                    }
                }
            }
        if (existChannel != null) {
            existChannel.touch()
            return existChannel
        }
        val newChannelId = ChannelId(lockForIdIterator.synchronize { channelIdIterator++ })
        gatewayClientService.sendCmd(
            ControlRequestDto(
                emmitChannel = ControlRequestDto.EmmitChannel(
                    id = newChannelId,
                    type = TransportType.WS_SPLIT,
                )
            )
        )
        logger.info("Wait new channel $newChannelId...")
        val result = withTimeoutOrNull(10.seconds) {
            val e = suspendCancellableCoroutine {
                it.invokeOnCancellation {
                    channelWaterLock.synchronize { channelWater.remove(newChannelId) }
                }
                channelWaterLock.synchronize { channelWater[newChannelId] = it }
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
    suspend fun connect(host: String, port: Int): TransportChannel {
        val channel = getOrCreateIdleChannel()
        gatewayClientService.sendCmd(
            ControlRequestDto(
                proxyConnect = ControlRequestDto.ProxyConnect(
                    id = channel.channel.id,
                    host = host,
                    port = port,
                )
            )
        )
        channel.locked.synchronize {
            channel.touch()
            channel.channel.description = "$host:$port"
            channel.isBusy = true
        }
        try {
            logger.info("Wait until gateway connect to $host:$port...")
            suspendCancellableCoroutine {
                it.invokeOnCancellation {
                    channel.locked.synchronize3 {
                        channel.isBusy = false
                    }
                    proxyWaterLock.synchronize { proxyWater.remove(channel.channel.id) }
                }
                proxyWaterLock.synchronize { proxyWater[channel.channel.id] = it }
            }
            logger.info("Gateway connected to $host:$port!")
            return channel.channel
        } catch (e: Throwable) {
            logger.info("Gateway can't connect to $host:$port!")
            channel.locked.synchronize3 {
                channel.isBusy = false
            }
            throw e
        }
    }

    fun assignBehavior(channel: TransportChannel, behavior: Behavior) {
        val wrapper = channelsLock.synchronize2 {
            channels[channel.id] ?: throw IllegalStateException("Channel ${channel.id} not found")
        }
        wrapper.locked.synchronize3 {
            wrapper.behavior = behavior
            wrapper.isBusy = false
        }
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
                channelsLock.synchronize2 { channels[channelId] }
                    ?.let {
                        it.locked.synchronize3 {
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
                    channel.locked.synchronize3 {
                        channel.isBusy = false
                    }
                    returnChannelToPool(channel)
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

    private suspend fun returnChannelToPool(channelWrapper: ChannelWrapper) {
        channelWrapper.locked.synchronize3 {
            val behavior = channelWrapper.behavior
            channelWrapper.behavior = null
            channelWrapper.isBusy = false
            channelWrapper.touch()
            behavior
        }?.asyncCloseAnyway()
    }

    suspend fun sendToPool(channel: TransportChannel) {
        if (channel.isClosed) {
            channelsLock.synchronize2 { channels.remove(channel.id) }
            return
        }
        val channelWrapper = channelsLock.synchronize2 { channels[channel.id] }
        if (channelWrapper == null) {
            channel.asyncClose()
            return
        }
        returnChannelToPool(channelWrapper)
    }
}
