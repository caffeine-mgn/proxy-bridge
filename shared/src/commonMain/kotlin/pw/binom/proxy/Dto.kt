package pw.binom.proxy

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf

@OptIn(ExperimentalSerializationApi::class)
object Dto {
    private val protobuf = ProtoBuf {

    }

    fun <T : Any> encode(ser: KSerializer<T>, value: T): ByteArray =
        protobuf.encodeToByteArray(ser, value)

    fun <T : Any> decode(ser: KSerializer<T>, data: ByteArray): T =
        protobuf.decodeFromByteArray(ser, data)
}
