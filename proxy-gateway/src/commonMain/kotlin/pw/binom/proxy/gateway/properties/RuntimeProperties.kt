package pw.binom.proxy.gateway.properties

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import pw.binom.*
import pw.binom.io.socket.DomainSocketAddress
import pw.binom.proxy.serialization.CommaStringList
import pw.binom.proxy.serialization.NetworkAddressSerializer
import pw.binom.proxy.serialization.URLSerializer
import pw.binom.url.URL
import pw.binom.url.toURL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
data class RuntimeProperties(
    @Serializable(URLSerializer::class)
    val url: URL,
    val proxy: Proxy? = null,
    val transportType: TransportType = TransportType.TCP_OVER_HTTP,
    val bufferSize: Int = DEFAULT_BUFFER_SIZE,
    val wsMasking: Boolean = true,
    val reconnectTimeout: Duration = 30.seconds,
    val pingInterval: Duration = 50.seconds
) {
    enum class TransportType {
        TCP_OVER_HTTP,
        WS,
        HTTP_POOLING,
    }

    @Serializable
    data class Proxy(
        @Serializable(NetworkAddressSerializer::class)
        val address: DomainSocketAddress,
        val auth: Auth? = null,
        @Serializable(CommaStringList::class)
        val onlyFor: List<String>? = null,
        @Serializable(CommaStringList::class)
        val noProxy: List<String>? = null,
        val bufferSize: Int = DEFAULT_BUFFER_SIZE
    )

    @Serializable
    data class Auth(
        val basicAuth: BasicAuth? = null,
        val bearerAuth: BearerAuth? = null,
    )

    @Serializable
    data class BasicAuth(
        val user: String,
        val password: String,
    )

    @Serializable
    data class BearerAuth(
        val token: String,
    )
}
