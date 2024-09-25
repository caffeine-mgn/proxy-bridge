package pw.binom.proxy

import kotlinx.serialization.Serializable

@Serializable
enum class TransportType {
//    TCP,
//    WS_LONG_CONNECT,

    /**
     * Подключение по WS, где каждый flush данных приходит отдельным сообщением
     */
    WS_SPLIT,
}
