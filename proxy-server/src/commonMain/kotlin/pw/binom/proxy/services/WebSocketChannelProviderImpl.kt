package pw.binom.proxy.services

/*
class WebSocketChannelProviderImpl : ChannelProvider, MetricProvider {
    private val metricProvider = MetricProviderImpl()
    private val proxyProperties by inject<ProxyProperties>()
    private val networkManager by inject<NetworkManager>()
//    private val gatewayClientService by inject<GatewayClientService>()
    private val remitCounterFail = metricProvider.counterLong("channel_re_emmit_fail")
    private val reemmitCounterSuccess = metricProvider.counterLong("channel_re_emmit_success")
    private val tcpConnectionFactory by inject<TcpConnectionFactory>()
    private val fileSystem by inject<FileSystem>(name = "LOCAL_FS")
    override val metrics: List<MetricUnit> by metricProvider
    private val connections = HashMap<String, Con>()
    private val logger by Logger.ofThisOrGlobal

    private class Con(
//        val connection: WebSocketConnection,
//        val frameChannel: WsFrameChannel,
        val manager: VirtualChannelManager,
    )

    suspend fun incomeConnection(id: TransportChannelId, connection: WebSocketConnection) {
        val manager = WsVirtualChannelManager(
            connection = connection,
            bufferSize = PackageSize(proxyProperties.bufferSize),
            channelProcessing = { channelId, channel ->
                GlobalScope.launch {
                    channel.useAsync { channel ->
                        WorkerChannelServer.start(
                            channel = channel,
                            tcpConnectionFactory = ServiceProvider.provide(tcpConnectionFactory),
                            fileSystem = ServiceProvider.provide(fileSystem),
                        )
                    }
                }
            }
        )
//        val channel = WsFrameChannel(
//            con = connection,
//            bufferSize = PackageSize(proxyProperties.bufferSize - 2),
//            channelId = id
//        )
        val con = Con(
            manager = manager
        )
        /*
        val con = Con(
//            connection = connection,
//            frameChannel = channel,
            manager = VirtualChannelManagerImpl(
                other = channel,
                tcpConnectionFactory = ServiceProvider.provide(tcpConnectionFactory),
                fileSystem = ServiceProvider.provide(fileSystem),
            ),
        )
        */
        channelLock.synchronize {
            connections[id.id] = con
        }
        channelWatersLock.synchronize {
            channelWaters.remove(id.id)
        }?.con?.resume(con)
        manager.useAsync { manager ->
            manager.processing(
                networkContext = networkManager,
            )
        }

//        con.manager.processing()
    }

    private val channelLock = SpinLock()
    private val channelWatersLock = SpinLock()
    private val channelWaters = HashMap<String, Water>()

    private class Water(
        var channelId: TransportChannelId,
        val con: CancellableContinuation<Con>
    ) {
        val startWait = DateTime.now
    }

    override suspend fun getNewChannel(): FrameChannel {
        val freeChannel = channelLock.synchronize {
            connections.values.find { it.manager.channelCount == 0 }
        }
        val validConnection = if (freeChannel == null) {
            val channelId = TransportChannelId(Random.nextUuid().toShortString())
            gatewayClientService.sendCmd(
                ControlRequestDto(
                    emmitChannel = ControlRequestDto.EmmitChannel(
                        id = channelId,
                        type = TransportType.WS_SPLIT,
                        bufferSize = PackageSize(proxyProperties.bufferSize),
                    )
                )
            )
            withTimeout(10.seconds) {
                suspendCancellableCoroutine<Con> {
                    it.invokeOnCancellation {
                        channelWatersLock.synchronize {
                            channelWaters.remove(channelId.id)
                        }
                    }
                    channelWatersLock.synchronize {
                        channelWaters[channelId.id] = Water(con = it, channelId = channelId)
                    }
                }
            }
        } else {
            freeChannel
        }
        val newChannelId = Random.nextInt().toShort()
        return validConnection.manager.getOrCreateChannel(ChannelId(newChannelId))
    }

    suspend fun channelFailShot(id: TransportChannelId) {
        val water = channelWatersLock.synchronize {
            channelWaters.remove(id.id) ?: return
        }
        if (DateTime.now - water.startWait < 10.seconds) {
            try {
//                val newId = lockForIdIterator.synchronize { channelIdIterator++ }
                val newId = TransportChannelId(Random.nextUuid().toShortString())
                water.channelId = newId
                gatewayClientService.sendCmd(
                    ControlRequestDto(
                        emmitChannel = ControlRequestDto.EmmitChannel(
                            id = water.channelId,
                            type = TransportType.WS_SPLIT,
                            bufferSize = PackageSize(proxyProperties.bufferSize),
                        )
                    )
                )
                channelWatersLock.synchronize {
                    channelWaters[water.channelId.id] = water
                }
                reemmitCounterSuccess.inc()
            } catch (e: Throwable) {
                remitCounterFail.inc()
                logger.warn("Can't re-emmit channel. old id: $id, new id: ${water.channelId}")
                forceCloseChannel(water)
            }
        } else {
            remitCounterFail.inc()
            forceCloseChannel(water)
        }
    }

    private suspend fun forceCloseChannel(water: Water) {
        water.con.resumeWithException(RuntimeException("Force close channel ${water.channelId}"))
        try {
            gatewayClientService.sendCmd(
                ControlRequestDto(
                    closeChannel = ControlRequestDto.CloseChannel(
                        id = water.channelId,
                    )
                )
            )
        } catch (e: Throwable) {
            // Do nothing
        }
    }
}
*/
