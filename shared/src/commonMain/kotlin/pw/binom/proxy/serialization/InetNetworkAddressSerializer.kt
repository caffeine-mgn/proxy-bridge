package pw.binom.proxy.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import pw.binom.io.socket.InetSocketAddress

@Serializer(InetSocketAddress::class)
object InetNetworkAddressSerializer : KSerializer<InetSocketAddress> {
    override val descriptor: SerialDescriptor
        get() = String.serializer().descriptor

    override fun deserialize(decoder: Decoder): InetSocketAddress {
        val str = decoder.decodeString()
        val items = str.split(':', limit = 2)
        return InetSocketAddress.resolve(
                host = items[0],
                port = items.getOrNull(1)?.toIntOrNull()
                        ?: throw SerializationException("Invalid NetworkAddress \"$str\"")
        )
    }

    override fun serialize(encoder: Encoder, value: InetSocketAddress) {
        encoder.encodeString("${value.host}:${value.port}")
    }
}
