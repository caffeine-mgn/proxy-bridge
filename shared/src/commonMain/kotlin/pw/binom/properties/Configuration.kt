package pw.binom.properties

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class Configuration(
    /**
     * Список проксей, которые нужно поднять и принимать подключение
     */
    val proxies: List<ProxyConfig> = emptyList(),

    /**
     * Список подключений, для ожидания подключения
     */
    val income: Income? = null,

    /**
     * Список подключений для перенаправления запроса
     */
    val outcome: Income? = null,

    /**
     * Настройка имен
     */
    val hostConfig: List<HostConfig> = emptyList(),
    val services: Set<Service> = emptySet(),
    val tcpForwarding: List<SocketForwarding> = emptyList(),
) {

    sealed interface TcpRule {
        data object Direct : TcpRule
        data class Target(val name: String) : TcpRule
    }

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

        @Serializable
        @SerialName("com")
        data class HttpProxy(val host: String, val port: String) : Outcome

        @Serializable
        @SerialName("com")
        data class Wrapper(val targetName: String) : Outcome
    }

    @Serializable
    sealed interface Income {
        @Serializable
        @SerialName("com")
        data class Com(val port: String, val speed: Int = 115200) : Income

//        @Serializable
//        @SerialName("tcp")
//        data class Tcp(val bind: String = "0.0.0.0", val port: String) : Income
//
//        @Serializable
//        @SerialName("ws")
//        data class WebSocket(val bind: String = "0.0.0.0", val port: String, val endpoint: String = "/ws") : Income
    }

    @Serializable
    sealed interface Service {
        @Serializable
        @SerialName("tcp-connect")
        data class TcpConnect(
            val hosts: List<HostConfig> = listOf(HostConfig(hosts = setOf("*"), filterMode = FilterMode.INCLUDE)),
        ) : Service
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
    data class ProxyConfig(val type: ProxyType, val bind: String = "0.0.0.0", val port: Int = 8080)

    @Serializable
    enum class ProxyType {
        @SerialName("http")
        HTTP,

        @SerialName("sock5")
        SOCKS5,
    }
}
