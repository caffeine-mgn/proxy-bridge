package pw.binom.proxy.properties

import kotlinx.serialization.Serializable
import pw.binom.*
import pw.binom.io.socket.InetSocketAddress
import pw.binom.proxy.serialization.DurationSecond
import pw.binom.proxy.serialization.InetNetworkAddressSerializer
import pw.binom.validate.annotations.LessOrEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
data class ProxyProperties(
    @LessOrEquals("65535")
    val bufferSize: Int = DEFAULT_BUFFER_SIZE,
    @Serializable(DurationSecond::class)
    val remoteClientAwaitTimeout: Duration = 5.seconds,
    @Serializable(DurationSecond::class)
    val channelIdleTime: Duration = 5.seconds,
    val externalBinds: List<@Serializable(InetNetworkAddressSerializer::class) InetSocketAddress> = listOf(
        InetSocketAddress.resolve(
            host = "0.0.0.0",
            port = 8086,
        ),
    ),
    val internalBinds: List<@Serializable(InetNetworkAddressSerializer::class) InetSocketAddress> = listOf(
        InetSocketAddress.resolve(
            host = "0.0.0.0",
            port = 8077,
        ),
    ),
    @Serializable(DurationSecond::class)
    val pingInterval: Duration = 30.seconds,
    val tcpCompressLevel: Int = 0
)
