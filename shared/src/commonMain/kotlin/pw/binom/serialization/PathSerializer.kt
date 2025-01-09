package pw.binom.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import pw.binom.url.Path
import pw.binom.url.toPath

object PathSerializer : KSerializer<Path> {
    override val descriptor: SerialDescriptor
        get() = String.serializer().descriptor

    override fun deserialize(decoder: Decoder): Path = decoder.decodeString().toPath

    override fun serialize(encoder: Encoder, value: Path) {
        encoder.encodeString(value.raw)
    }
}
