package pw.binom.properties

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class Configuration(
    /**
     * Список проксей, которые нужно поднять и принимать подключение
     */
    val services: List<Service> = emptyList(),

    /**
     * Список подключений, для ожидания подключения
     */
    val incomes: List<Income> = emptyList(),

    /**
     * Список подключений для перенаправления запроса
     */
    val outcomes: Map<String, Outcome>? = null,

    /**
     * Настройка имен
     */
    val hostConfig: List<HostConfig> = emptyList(),
    val tcpForwarding: List<SocketForwarding> = emptyList(),
    val trafficRoute: List<TrafficRoute> = emptyList(),
) {

    @Serializable
    data class SocketForwarding(
        val localPort: Int,
        val remoteHost: String,
        val remotePort: Int,
    )

    @Serializable
    sealed interface Outcome {
        @Serializable
        @SerialName("com")
        data class Com(val port: String, val speed: Int = 115200) : Outcome
        data class Tcp(val host: String = "0.0.0.0", val port: Int) : Outcome
        data class Wrapper(val outcome: String) : Outcome
    }

    @Serializable
    sealed interface Income {
        @Serializable
        @SerialName("com")
        data class Com(val port: String, val speed: Int = 115200) : Income

        @Serializable
        @SerialName("tcp")
        data class Tcp(val bind: String = "0.0.0.0", val port: Int) : Income
//
//        @Serializable
//        @SerialName("ws")
//        data class WebSocket(val bind: String = "0.0.0.0", val port: String, val endpoint: String = "/ws") : Income
    }

    @Serializable
    data class Auth(val username: String, val password: String)

    @Serializable
    sealed interface Egress {
        @Serializable
        @SerialName("direct")
        object Direct : Egress

        @Serializable
        @SerialName("http-proxy")
        class HttpProxy(val host: String, val port: Int, val auth: Auth? = null) : Egress

        @SerialName("outcome")
        @Serializable
        class Outcome(val outcome: String) : Egress
    }

    @Serializable
    sealed interface TrafficRoute {
        val rule: Egress

        @Serializable
        @SerialName("by-domain")
        class ByDomain(val host: String, override val rule: Egress) : TrafficRoute

        @Serializable
        @SerialName("always")
        class Always(override val rule: Egress) : TrafficRoute
    }

    @Serializable
    enum class FilterMode {
        @SerialName("include")
        INCLUDE,

        @SerialName("exclude")
        EXCLUDE,
    }

    @Serializable
    data class HostConfig(
        val hosts: Set<String>,
        val filterMode: FilterMode,
    )

    @Serializable
    sealed interface Service {
        @Serializable
        @SerialName("http-proxy")
        data class HttpProxy(val bind: String = "0.0.0.0", val port: Int = 8080) : Service

        @Serializable
        @SerialName("socks5-proxy")
        data class Socks5(val bind: String = "0.0.0.0", val port: Int = 1080) : Service

        @Serializable
        @SerialName("tcp-port-forward")
        data class TcpPortForward(
            val bind: String = "0.0.0.0",
            val localPort: Int = 1080,
            val remotePort: Int,
            val remoteHost: String,
        ) : Service
    }
}
