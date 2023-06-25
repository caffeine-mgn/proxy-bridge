package pw.binom.proxy

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import pw.binom.url.URL
import pw.binom.url.toURL

object URLSerializer : KSerializer<URL> {
    override val descriptor: SerialDescriptor
        get() = String.serializer().descriptor

    override fun deserialize(decoder: Decoder): URL = decoder.decodeString().toURL()

    override fun serialize(encoder: Encoder, value: URL) {
        encoder.encodeString(value.toString())
    }

}

@Serializable
data class RuntimeProperties(
    @Serializable(URLSerializer::class)
    val url: URL,
    val auth: Auth? = null,
    val proxy: Proxy? = null,
) {
    @Serializable
    data class Proxy(
        @Serializable(URLSerializer::class)
        val url: URL,
        val proxyAuth: Auth? = null,
    )

    @Serializable
    data class Auth(
        val user: String,
        val password: String,
    )
}