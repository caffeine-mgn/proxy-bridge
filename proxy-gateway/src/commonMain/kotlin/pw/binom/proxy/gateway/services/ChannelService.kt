package pw.binom.proxy.gateway.services

import pw.binom.*
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.http.BasicAuth
import pw.binom.io.http.BearerAuth
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.connectWebSocket
import pw.binom.io.socket.DomainSocketAddress
import pw.binom.logger.Logger
import pw.binom.network.NetworkManager
import pw.binom.network.tcpConnect
import pw.binom.proxy.BridgeJob
import pw.binom.proxy.ChannelId
import pw.binom.proxy.TransportType
import pw.binom.proxy.Urls
import pw.binom.proxy.channels.TransportChannel
import pw.binom.proxy.channels.WSSpitChannel
import pw.binom.proxy.gateway.properties.RuntimeProperties
import pw.binom.proxy.gateway.tcpConnectViaHttpProxy
import pw.binom.strong.inject
import pw.binom.url.isWildcardMatch

class ChannelService {
    private val runtimeProperties by inject<RuntimeProperties>()
    private val httpClient by inject<HttpClient>()
    private val channels = HashMap<ChannelId, TransportChannel>()
    private val networkManager by inject<NetworkManager>()

    /**
     * Lock for [channels]
     */
    private val channelsLock = SpinLock()

    suspend fun createChannel(channelId: ChannelId, type: TransportType): TransportChannel {
        val newChannel = when (type) {
            TransportType.WS_SPLIT -> {
                val transportUrl = runtimeProperties.url.addPath(Urls.TRANSPORT_WS.toPath { channelId.id.toString() })
                val transportConnection = httpClient.connectWebSocket(
                    uri = transportUrl,
                    masking = runtimeProperties.wsMasking,
                ).start(bufferSize = runtimeProperties.bufferSize)
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

    suspend fun connect(
        channelId: ChannelId,
        host: String,
        port: Short,
    ): BridgeJob {
        val channel =
            channelsLock.synchronize { channels[channelId] } ?: throw RuntimeException("Channel $channelId not found")
        val address = DomainSocketAddress(
            host = host,
            port = port.toInt()
        )
        val connection = runtimeProperties.proxy?.let PROXY@{ proxy ->
            proxy.noProxy?.let { noProxy ->
                if (noProxy.any { host.isWildcardMatch(it) }) {
                    return@PROXY null
                }
            }
            proxy.onlyFor?.let { onlyFor ->
                if (onlyFor.none { host.isWildcardMatch(it) }) {
                    return@PROXY null
                }
            }
            networkManager.tcpConnectViaHttpProxy(
                proxy = proxy.address.resolve(),
                address = address,
                auth = proxy.auth?.let { auth ->
                    when {
                        auth.basicAuth != null ->
                            BasicAuth(
                                login = auth.basicAuth.user,
                                password = auth.basicAuth.password
                            )

                        auth.bearerAuth != null -> BearerAuth(token = auth.bearerAuth.token)
                        else -> null
                    }
                },
                readBufferSize = proxy.bufferSize,
            )
        } ?: networkManager
            .tcpConnect(
                address = DomainSocketAddress(
                    host = host,
                    port = port.toInt()
                ).resolve(),
            )

        return channel.connectWith(
            other = connection,
            bufferSize = DEFAULT_BUFFER_SIZE,
        )
    }

    fun reset(channelId: ChannelId) {
        val channel =
            channelsLock.synchronize { channels[channelId] } ?: throw RuntimeException("Channel $channelId not found")
        channel.breakCurrentRole()
    }
}

