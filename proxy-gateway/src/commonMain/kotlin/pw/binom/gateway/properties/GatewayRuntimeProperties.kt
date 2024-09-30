package pw.binom.gateway.properties

import kotlinx.serialization.Serializable
import pw.binom.*
import pw.binom.io.socket.DomainSocketAddress
import pw.binom.proxy.serialization.CommaStringList
import pw.binom.proxy.serialization.NetworkAddressSerializer
import pw.binom.proxy.serialization.URLSerializer
import pw.binom.url.URL
import pw.binom.validate.annotations.Greater
import pw.binom.validate.annotations.NotBlank
import pw.binom.validate.annotations.OneOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
data class GatewayRuntimeProperties(
    @Serializable(URLSerializer::class)
    val url: URL,
    val proxy: Proxy? = null,
    val transportType: TransportType = TransportType.TCP_OVER_HTTP,
    @Greater("0")
    val bufferSize: Int = DEFAULT_BUFFER_SIZE,
    val wsMasking: Boolean = true,
    val reconnectTimeout: Duration = 30.seconds,
    val pingInterval: Duration = 50.seconds,
    val reconnectDelay: Duration = 50.seconds,
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
        @Greater("0")
        val bufferSize: Int = DEFAULT_BUFFER_SIZE
    )

    @Serializable
    @OneOf(
        "basicAuth",
        "bearerAuth",
    )
    data class Auth(
        val basicAuth: BasicAuth? = null,
        val bearerAuth: BearerAuth? = null,
    )

    @Serializable
    data class BasicAuth(
        @NotBlank
        val user: String,
        @NotBlank
        val password: String,
    )

    @Serializable
    data class BearerAuth(
        @NotBlank
        val token: String,
    )
}
