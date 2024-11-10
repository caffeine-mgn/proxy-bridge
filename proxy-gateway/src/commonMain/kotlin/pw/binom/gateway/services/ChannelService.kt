package pw.binom.gateway.services

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import pw.binom.*
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.connectWebSocket
import pw.binom.proxy.TransportChannelId
import pw.binom.TransportType
import pw.binom.Urls
import pw.binom.frame.PackageSize
import pw.binom.gateway.properties.GatewayRuntimeProperties
import pw.binom.io.FileSystem
import pw.binom.io.http.Headers
import pw.binom.io.httpClient.addHeader
import pw.binom.io.useAsync
import pw.binom.strong.ServiceProvider
import pw.binom.strong.inject
import pw.binom.subchannel.WorkerChannelServer

class ChannelService {
    private val runtimeProperties by inject<GatewayRuntimeProperties>()
    private val httpClient by inject<HttpClient>()
    private val channels = HashMap<TransportChannelId, Job>()
//    private val networkManager by inject<NetworkManager>()
//    private val proxyClient by inject<ProxyClient>()
    private val tcpConnectionFactory by inject<TcpConnectionFactoryImpl>()
    private val channelBehaviors = HashMap<TransportChannelId, Behavior>()
    private val fileSystem by inject<FileSystem>(name = "LOCAL_FS")

    /**
     * Lock for [channels]
     */
    private val channelsLock = SpinLock()

    suspend fun createChannel(
        channelId: TransportChannelId,
        type: TransportType,
        bufferSize: PackageSize,
    ) {
        val newChannel = when (type) {
            TransportType.WS_SPLIT -> {
                val transportUrl = runtimeProperties.url.addPath(Urls.TRANSPORT_WS.toPath { channelId.asString })
                val transportConnection = httpClient.connectWebSocket(
                    uri = transportUrl,
                ).also {
                    it.headers.add("X-trace", channelId.asString)
                    it.addHeader(
                        Headers.USER_AGENT,
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/102.0.5005.197 Safari/537.36"
                    )
                }.start(bufferSize = runtimeProperties.bufferSize)
                val wsChannel = WsFrameChannel(
                    con = transportConnection,
                    bufferSize = bufferSize,
                    channelId = channelId,
                )
                WsVirtualChannelManager(
                    connection = transportConnection,
                    bufferSize = bufferSize,
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
//                VirtualChannelManagerImpl(
//                    other = wsChannel,
//                    tcpConnectionFactory = ServiceProvider.provide(tcpConnectionFactory),
//                    fileSystem = ServiceProvider.provide(fileSystem),
//                )
            }
        }
        val processingJob = GlobalScope.launch {
            newChannel.useAsync {
                it.processing()
            }
        }
        channelsLock.synchronize { channels[channelId] = processingJob }
    }

    suspend fun getChannel(channelId: TransportChannelId) =
        channelsLock.synchronize { channels[channelId] } ?: throw RuntimeException("Channel $channelId not found")

//    private val tcpCommunicatePair by inject<TcpCommunicatePair>()

    suspend fun connect(
        channelId: TransportChannelId,
        host: String,
        port: Int,
        compressLevel: Int,
    ) {
        TODO()
//        val channel =
//            channelsLock.synchronize { channels[channelId] } ?: throw RuntimeException("Channel $channelId not found")
//        GlobalScope.launch(networkManager) {
//            tcpCommunicatePair.startClient(
//                data = TcpClientData(
//                    host = host,
//                    port = port.toShort(),
//                ),
//                channel = channel,//.withLogger("$host:$port"),
//            )
//        }
    }

//    suspend fun reset(channelId: ChannelId) {
//        val behavior = channelsLock.synchronize {
//            channelBehaviors.remove(channelId)
//        } ?: return
//        behavior.asyncClose()
//    }

    suspend fun close(id: TransportChannelId) {
        val (channel, behavior) = channelsLock.synchronize {
            channels.remove(id) to channelBehaviors.remove(id)
        }
        behavior?.asyncCloseAnyway()
        channel?.cancelAndJoin()
    }
}

