package pw.binom.proxy

import kotlinx.serialization.Serializable
import pw.binom.DEFAULT_BUFFER_SIZE
import pw.binom.io.socket.InetNetworkAddress
import pw.binom.proxy.serialization.InetNetworkAddressSerializer


@Serializable
data class RuntimeProperties(
        val bufferSize: Int = DEFAULT_BUFFER_SIZE,
        val externalBinds: List<@Serializable(InetNetworkAddressSerializer::class) InetNetworkAddress> = listOf(
                InetNetworkAddress.create(
                        host = "0.0.0.0",
                        port = 8080,
                )
        ),
        val internalBinds: List<@Serializable(InetNetworkAddressSerializer::class) InetNetworkAddress> = listOf(
                InetNetworkAddress.create(
                        host = "0.0.0.0",
                        port = 8081,
                )
        ),
)