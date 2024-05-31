package pw.binom.proxy.node

import kotlinx.serialization.Serializable
import pw.binom.*
import pw.binom.io.socket.InetSocketAddress
import pw.binom.proxy.serialization.InetNetworkAddressSerializer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
data class RuntimeClientProperties(
    val bufferSize: Int = DEFAULT_BUFFER_SIZE,
    val remoteClientAwaitTimeout: Duration = 5.seconds,
    val externalBinds: List<@Serializable(InetNetworkAddressSerializer::class) InetSocketAddress> = listOf(
        InetSocketAddress.resolve(
            host = "0.0.0.0",
            port = 8080,
        ),
    ),
    val internalBinds: List<@Serializable(InetNetworkAddressSerializer::class) InetSocketAddress> = listOf(
        InetSocketAddress.resolve(
            host = "0.0.0.0",
            port = 8081,
        ),
    ),
    val pingInterval: Duration = 30.seconds,
)
