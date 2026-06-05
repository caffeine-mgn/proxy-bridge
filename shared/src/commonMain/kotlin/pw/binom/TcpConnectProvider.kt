package pw.binom

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readLine
import io.ktor.utils.io.writeByteArray
import kotlin.io.encoding.Base64

interface TcpConnectProvider {
    sealed interface ConnectResult {
        interface Success : ConnectResult, AutoCloseable {
            val readChannel: ByteReadChannel
            val writeChannel: ByteWriteChannel
        }

        class Error(val msg: String) : ConnectResult
        object UnknownError : ConnectResult
        object Unreachable : ConnectResult
    }


    suspend fun connect(host: String, port: Int): ConnectResult

    private class SuccessImpl(
        override val readChannel: ByteReadChannel,
        override val writeChannel: ByteWriteChannel,
        val socket: Socket,
    ) : ConnectResult.Success {
        override fun close() {
            socket.close()
        }

    }

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

    data class Auth(val login: String, val password: String)

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
}
