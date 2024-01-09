package pw.binom.proxy.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import pw.binom.io.ByteArrayInput
import pw.binom.io.ByteArrayOutput
import pw.binom.io.use

interface ShortSerialization {
    val serializersModule: SerializersModule

    companion object : ShortSerialization {
        override val serializersModule: SerializersModule = EmptySerializersModule()
    }

    fun <T> encodeByteArray(
        serializer: SerializationStrategy<T>,
        value: T,
    ): ByteArray {
        val out = ByteArrayOutput()
        val encoder =
            ShortEncoder(
                out = out,
                serializersModule = serializersModule
            )
        serializer.serialize(encoder, value)
        return out.toByteArray()
    }

    fun <T> decodeByteArray(
        serializer: DeserializationStrategy<T>,
        array: ByteArray,
    ) = ByteArrayInput(array).use { input ->
        val decoder =
            ShortDecoder(
                serializersModule = serializersModule,
                input = input,
                desc = serializer.descriptor
            )
        serializer.deserialize(decoder)
    }
}
