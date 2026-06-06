package pw.binom

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readLine
import io.ktor.utils.io.writeByteArray
import pw.binom.channel.TcpConnectChannel
import pw.binom.properties.OutcomeService
import pw.binom.utils.StringUtils
import pw.binom.utils.toByteChannel
import java.io.StringWriter
import kotlin.io.encoding.Base64

/**
 * Провайдер TCP-соединений.
 * Абстрагирует способ установки TCP-соединения: напрямую, через прокси или через канальный транспорт.
 */
interface TcpConnectProvider {
    /**
     * Результат попытки соединения.
     */
    sealed interface ConnectResult {
        /**
         * Успешное соединение с каналами чтения и записи.
         */
        interface Success : ConnectResult, AutoCloseable {
            val readChannel: ByteReadChannel
            val writeChannel: ByteWriteChannel
        }

        /**
         * Ошибка соединения с конкретным сообщением.
         */
        class Error(val msg: String) : ConnectResult

        /**
         * Ошибка соединения по неизвестной причине.
         */
        object UnknownError : ConnectResult

        /**
         * Удалённый хост недоступен.
         */
        object Unreachable : ConnectResult
    }


    suspend fun connect(host: String, port: Int): ConnectResult

    private class SuccessImpl(
        override val readChannel: ByteReadChannel,
        override val writeChannel: ByteWriteChannel,
        val socket: AutoCloseable,
    ) : ConnectResult.Success {
        override fun close() {
            socket.close()
        }
    }

    /**
     * Прямое TCP-соединение через Ktor socket API.
     */
    class Direct(val selector: SelectorManager) : TcpConnectProvider {
        override suspend fun connect(host: String, port: Int): ConnectResult {
            val socket = try {
                aSocket(selector).tcp().connect(
                    hostname = host,
                    port = port,
                )
            } catch (e: Throwable) {
                return e.message?.let { ConnectResult.Error(it) } ?: ConnectResult.UnknownError
            }
            return SuccessImpl(
                readChannel = socket.openReadChannel(),
                writeChannel = socket.openWriteChannel(),
                socket = socket,
            )
        }
    }

    /**
     * TCP-соединение через канальный транспорт (OutcomeService).
     * Используется когда соединение идёт через relay/proxy-bridge инфраструктуру.
     */
    class UsingOutcome(val outcomeService: OutcomeService) : TcpConnectProvider {
        override suspend fun connect(host: String, port: Int): ConnectResult {
            val channel = outcomeService.createChannel()
            val tcpChannel = TcpConnectChannel.connect(
                channel = channel,
                host = host,
                port = port,
            )
            if (tcpChannel == null) {
                runCatching { channel.close() }
                return ConnectResult.Unreachable
            }

            return SuccessImpl(
                readChannel = tcpChannel.income.toByteChannel(),
                writeChannel = tcpChannel.outcome.toByteChannel(),
                socket = channel,
            )
        }

    }

    /**
     * Учётные данные для аутентификации на прокси.
     */
    data class Auth(val login: String, val password: String)

    /**
     * TCP-соединение через HTTP-прокси с использованием метода CONNECT.
     * Поддерживает опциональную Basic-аутентификацию.
     */
    class HttpProxy(
        private val selector: SelectorManager,
        private val host: String,
        private val port: Int,
        private val auth: Auth?,
    ) : TcpConnectProvider {
        override suspend fun connect(host: String, port: Int): ConnectResult {
            val socket = aSocket(selector).tcp().connect(
                hostname = this.host,
                port = this.port,
            )
            val readChannel = socket.openReadChannel()
            val writeChannel = socket.openWriteChannel()
            val connectBytes = buildString {
                append("CONNECT $host:$port HTTP/1.1\r\n")
                append("Host: $host:$port\r\n")
                append("Proxy-Connection: keep-alive\r\n")
                if (auth != null) {
                    val encoded = Base64.encode("${auth.login}:${auth.password}".encodeToByteArray())
                    append("Proxy-Authorization: Basic $encoded\r\n")
                }
                append("\r\n")
            }.encodeToByteArray()
            writeChannel.writeByteArray(connectBytes)
            writeChannel.flush()
            val line = readChannel.readLine() ?: return ConnectResult.UnknownError
            val code = line.split(' ')[1].toInt()
            while (true) {
                val line = readChannel.readLine() ?: return ConnectResult.UnknownError
                if (line.isEmpty()) {
                    break
                }
            }
            return when (code) {
                200 -> SuccessImpl(
                    socket = socket,
                    readChannel = readChannel,
                    writeChannel = writeChannel,
                )

                502 -> ConnectResult.Unreachable

                else -> ConnectResult.Error("Invalid response code: $code")
            }
        }
    }

    class ByRule(val rules: List<Rule>) : TcpConnectProvider {
        private val logger = KotlinLogging.logger {}

        sealed interface Rule {
            val provider: TcpConnectProvider
            fun canConnect(host: String, port: Int): Boolean

            class ByDomain(val host: String, override val provider: TcpConnectProvider) : Rule {
                override fun canConnect(host: String, port: Int): Boolean =
                    StringUtils.wildcardMatch(string = host, wildcard = this.host)

            }

            class Always(override val provider: TcpConnectProvider) : Rule {
                override fun canConnect(host: String, port: Int): Boolean = true
            }
        }

        override suspend fun connect(host: String, port: Int): ConnectResult {

            rules.forEach { rule ->
                if (rule.canConnect(host, port)) {
                    logger.info { "Use rule ${rule::class.simpleName} for connect to $host:$port" }
                    val result = rule.provider.connect(host, port)
                    logger.info { "Connect result: $result" }
                    return result
                }
            }
            return ConnectResult.Unreachable
        }
    }
}
