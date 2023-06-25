package pw.binom.proxy

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import pw.binom.io.socket.NetworkAddress
import pw.binom.proxy.serialization.NetworkAddressSerializer


@Serializable
data class RuntimeProperties(
    val externalBinds: List<@Serializable(NetworkAddressSerializer::class) NetworkAddress> = listOf(
        NetworkAddress.create(
            host = "0.0.0.0",
            port = 8080,
        )
    ),
    val internalBinds: List<@Serializable(NetworkAddressSerializer::class) NetworkAddress> = listOf(
        NetworkAddress.create(
            host = "0.0.0.0",
            port = 8081,
        )
    ),
)