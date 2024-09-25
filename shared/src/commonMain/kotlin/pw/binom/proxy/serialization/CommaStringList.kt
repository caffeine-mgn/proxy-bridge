package pw.binom.proxy.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object CommaStringList : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor = String.serializer().descriptor

    override fun deserialize(decoder: Decoder): List<String> =
        decoder.decodeString().split(',')

    override fun serialize(encoder: Encoder, value: List<String>) {
        encoder.encodeString(value.joinToString(separator = ","))
    }
}
