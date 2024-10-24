package pw.binom.gateway.services

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import pw.binom.*
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.connectWebSocket
import pw.binom.logger.Logger
import pw.binom.network.NetworkManager
import pw.binom.proxy.ChannelId
import pw.binom.TransportType
import pw.binom.Urls
import pw.binom.gateway.behaviors.ConnectTcpBehavior
import pw.binom.proxy.ProxyClient
import pw.binom.proxy.channels.TransportChannel
import pw.binom.proxy.channels.WSSpitChannel
import pw.binom.gateway.properties.GatewayRuntimeProperties
import pw.binom.io.http.Headers
import pw.binom.io.httpClient.addHeader
import pw.binom.strong.inject

class ChannelService {
    private val runtimeProperties by inject<GatewayRuntimeProperties>()
    private val httpClient by inject<HttpClient>()
    private val channels = HashMap<ChannelId, TransportChannel>()
    private val networkManager by inject<NetworkManager>()
    private val proxyClient by inject<ProxyClient>()
    private val tcpConnectionFactory by inject<TcpConnectionFactoryImpl>()
    private val channelBehaviors = HashMap<ChannelId, Behavior>()

    /**
     * Lock for [channels]
     */
    private val channelsLock = SpinLock()

    suspend fun createChannel(channelId: ChannelId, type: TransportType): TransportChannel {
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
                WSSpitChannel(
                    id = channelId,
                    connection = transportConnection.connection,
                    logger = Logger.getLogger("GATEWAY")
                )
            }
        }
        channelsLock.synchronize { channels[channelId] = newChannel }
        return newChannel
    }

    suspend fun getChannel(channelId: ChannelId) =
        channelsLock.synchronize { channels[channelId] } ?: throw RuntimeException("Channel $channelId not found")

    suspend fun connect(
        channelId: ChannelId,
        host: String,
        port: Int,
        compressLevel: Int,
    ) {
        val channel =
            channelsLock.synchronize { channels[channelId] } ?: throw RuntimeException("Channel $channelId not found")
        val behavior = ConnectTcpBehavior.start(
            client = proxyClient,
            from = channel,
            host = host,
            port = port,
            tcpConnectionFactory = tcpConnectionFactory,
            compressLevel = compressLevel,
        )
        channel.description = "$host:$port"
        if (behavior != null) {
            channelsLock.synchronize {
                channelBehaviors[channelId] = behavior
            }
            GlobalScope.launch(networkManager) {
                behavior.run()
            }
        }
    }

    suspend fun reset(channelId: ChannelId) {
        val behavior = channelsLock.synchronize {
            channelBehaviors.remove(channelId)
        } ?: return
        behavior.asyncClose()
    }

    suspend fun close(id: ChannelId) {
        val (channel, behavior) = channelsLock.synchronize {
            channels.remove(id) to channelBehaviors.remove(id)
        }
        behavior?.asyncCloseAnyway()
        channel?.asyncCloseAnyway()
    }
}

