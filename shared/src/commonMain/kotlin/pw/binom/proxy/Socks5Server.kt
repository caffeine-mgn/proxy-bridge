package pw.binom.proxy

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.net.InetAddress
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class Socks5Server(
    private val port: Int = 1080,
    private val selectorManager: SelectorManager,
    private val authProvider: Socks5AuthProvider? = null,
    private val onConnect: ConnectProcessing = ConnectProcessing { _, _, _ -> },
) : AutoCloseable {
    companion object {
        // SOCKS5 Version
        private const val VERSION = 0x05

        // Authentication Methods [[37]]
        private const val AUTH_NONE = 0x00
        private const val AUTH_GSSAPI = 0x01
        private const val AUTH_PASSWORD: UByte = 0x02u
        private const val AUTH_NO_ACCEPT: UByte = 0xFFu

        // Commands [[24]]
        private const val CMD_CONNECT = 0x01
        private const val CMD_BIND = 0x02
        private const val CMD_UDP_ASSOCIATE = 0x03

        // Address Types [[24]]
        private const val ATYP_IPV4 = 0x01
        private const val ATYP_DOMAIN = 0x03
        private const val ATYP_IPV6 = 0x04

        // Reply Codes [[49]][[50]]
        private const val REP_SUCCEEDED = 0x00
        private const val REP_GENERAL_FAILURE = 0x01
        private const val REP_NOT_ALLOWED = 0x02
        private const val REP_NETWORK_UNREACHABLE = 0x03
        private const val REP_HOST_UNREACHABLE = 0x04
        private const val REP_CONNECTION_REFUSED = 0x05
        private const val REP_TTL_EXPIRED = 0x06
        private const val REP_COMMAND_NOT_SUPPORTED = 0x07
        private const val REP_ADDRESS_NOT_SUPPORTED = 0x08
    }

    private val logger = KotlinLogging.logger { }

    private val serverJob = selectorManager.launch {
        aSocket(selectorManager).tcp()
            .bind("0.0.0.0", port)
            .use { server ->
                logger.info { "SOCKS5 server started on port $port" }
                while (isActive) {
                    val client = server.accept()
                    launch {
                        try {
                            handleClient(client)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            client.close()
                        }
                    }
                }
            }
    }


    private suspend fun handleClient(client: Socket) {
        client.use {
            val input = it.openReadChannel()
            val output = it.openWriteChannel(autoFlush = true)

            // 1. Method Negotiation [[1]]
            val version = input.readByte()
            if (version != VERSION.toByte()) {
                output.writeByte(0x00) // Отказ
                return
            }

            val nMethods = input.readByte().toInt()
            val methods = ByteArray(nMethods) { input.readByte() }

            // Выбор метода аутентификации
            val selectedMethod = selectAuthMethod(methods)
            output.writeByte(VERSION.toByte())
            output.writeByte(selectedMethod)

            if (selectedMethod == AUTH_NO_ACCEPT.toByte()) {
                return
            }

            // 2. Authentication (если требуется) [[36]]
            if (selectedMethod == AUTH_PASSWORD.toByte()) {
                if (!handlePasswordAuth(input, output)) {
                    return
                }
            }

            // 3. Request
            val requestVersion = input.readByte()
            val command = input.readByte().toInt()
            input.readByte() // RSV
            val addressType = input.readByte().toInt()

            // Парсинг адреса назначения
            val (host, port) = parseAddress(input, addressType)

            // Обработка команд
            when (command) {
                CMD_CONNECT -> handleConnect(input, output, host, port)
                CMD_BIND -> handleBind(input, output, host, port)
                CMD_UDP_ASSOCIATE -> handleUdpAssociate(input, output, host, port)
                else -> sendReply(output, REP_COMMAND_NOT_SUPPORTED)
            }
        }
    }

    private fun selectAuthMethod(clientMethods: ByteArray): Byte {
        if (authProvider == null) {
            return if (clientMethods.contains(AUTH_NONE.toByte()))
                AUTH_NONE.toByte() else AUTH_NO_ACCEPT.toByte()
        }

        return when {
            clientMethods.contains(AUTH_PASSWORD.toByte()) -> AUTH_PASSWORD.toByte()
            clientMethods.contains(AUTH_NONE.toByte()) -> AUTH_NONE.toByte()
            else -> AUTH_NO_ACCEPT.toByte()
        }
    }

    private suspend fun handlePasswordAuth(
        input: ByteReadChannel,
        output: ByteWriteChannel
    ): Boolean {
        val authVersion = input.readByte()
        if (authVersion != 0x01.toByte()) return false

        val ulen = input.readByte().toInt()
        val username = ByteArray(ulen) { input.readByte() }.decodeToString()

        val plen = input.readByte().toInt()
        val password = ByteArray(plen) { input.readByte() }.decodeToString()

        val authenticated = authProvider?.authenticate(username, password) ?: false

        output.writeByte(0x01)
        output.writeByte(if (authenticated) 0x00 else 0x01)

        return authenticated
    }

    private suspend fun parseAddress(
        input: ByteReadChannel,
        addressType: Int
    ): Pair<String, Int> {
        return when (addressType) {
            ATYP_IPV4 -> {
                val ip = ByteArray(4) { input.readByte() }
                    .joinToString(".") { it.toInt().and(0xFF).toString() }
                val port = input.readShort().toInt() and 0xFFFF
                ip to port
            }

            ATYP_DOMAIN -> {
                val domainLength = input.readByte().toInt()
                val domain = ByteArray(domainLength) { input.readByte() }.decodeToString()
                val port = input.readShort().toInt() and 0xFFFF
                domain to port
            }

            ATYP_IPV6 -> {
                val ip = ByteArray(16) { input.readByte() }.toList()
                    .chunked(2)
                    .joinToString(":") { bytes ->
                        bytes.joinToString("") { it.toInt().and(0xFF).toString(16) }
                    }
                val port = input.readShort().toInt() and 0xFFFF
                ip to port
            }

            else -> throw IllegalArgumentException("Unsupported address type: $addressType")
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun handleConnect(
        input: ByteReadChannel,
        output: ByteWriteChannel,
        host: String,
        port: Int
    ) {
        val finished = AtomicBoolean(false)
        fun finished() {
            check(finished.compareAndSet(false, true)) { "Already finished" }
        }

        val context = object : ProxyingRawContext {
            override suspend fun ok(): Pair<ByteReadChannel, ByteWriteChannel> {
                finished()
                sendReply(output, REP_SUCCEEDED, "0.0.0.0", 0)
                return input to output
            }

            override suspend fun ioError() {
                finished()
                sendReply(output, REP_GENERAL_FAILURE)
            }

            override suspend fun timeout() {
                finished()
                ioError()
            }

            override suspend fun notAvailable() {
                finished()
                ioError()
            }

            override suspend fun connectionRefused() {
                finished()
                sendReply(output, REP_CONNECTION_REFUSED)
            }

            override suspend fun noRouteToHostException() {
                finished()
                sendReply(output, REP_HOST_UNREACHABLE)
            }

            override suspend fun unknownHostException() {
                finished()
                sendReply(output, REP_HOST_UNREACHABLE)
            }
        }
        onConnect.connect(
            host = host,
            port = port,
            context = context,
        )
        if (!finished.load()) {
            context.ioError()
        }
        /*
        try {
            val targetSocket = aSocket(selectorManager).tcp()
                .connect(InetSocketAddress(host, port))

            // Успешное подключение
            sendReply(output, REP_SUCCEEDED, "0.0.0.0", 0)

            // Relay traffic
            relayTraffic(input, output, targetSocket)

        } catch (e: Exception) {
            val replyCode = when (e) {
                is java.net.ConnectException -> REP_CONNECTION_REFUSED
                is java.net.NoRouteToHostException -> REP_HOST_UNREACHABLE
                is java.net.UnknownHostException -> REP_HOST_UNREACHABLE
                else -> REP_GENERAL_FAILURE
            }
            sendReply(output, replyCode)
        }
        */
    }

    private suspend fun handleBind(
        input: ByteReadChannel,
        output: ByteWriteChannel,
        host: String,
        port: Int
    ) {
        // BIND: сервер слушает и ждёт входящее соединение
        // Используется для FTP и подобных протоколов [[26]]
        sendReply(output, REP_COMMAND_NOT_SUPPORTED)
    }

    private suspend fun handleUdpAssociate(
        input: ByteReadChannel,
        output: ByteWriteChannel,
        host: String,
        port: Int
    ) {
        // UDP ASSOCIATE: требует UDP сокет [[32]]
        // Критично для торрент-клиентов, VoIP
        sendReply(output, REP_COMMAND_NOT_SUPPORTED)
    }

    private suspend fun sendReply(
        output: ByteWriteChannel,
        replyCode: Int,
        bindAddress: String = "0.0.0.0",
        bindPort: Int = 0
    ) {
        output.writeByte(VERSION.toByte())
        output.writeByte(replyCode.toByte())
        output.writeByte(0x00) // RSV
        output.writeByte(ATYP_IPV4.toByte())
        output.writeByteArray(bindAddress.split(".").map { it.toInt().toByte() }.toByteArray())
        output.writeShort(bindPort.toShort())
    }

    private suspend fun relayTraffic(
        clientInput: ByteReadChannel,
        clientOutput: ByteWriteChannel,
        targetSocket: Socket
    ) {
        val targetInput = targetSocket.openReadChannel()
        val targetOutput = targetSocket.openWriteChannel(autoFlush = true)

        coroutineScope {
            val job1 = launch {
                try {
                    clientInput.copyTo(targetOutput)
                } finally {
                    targetOutput.close()
                }
            }
            val job2 = launch {
                try {
                    targetInput.copyTo(clientOutput)
                } finally {
                    clientOutput.close()
                }
            }
            job1.join()
            job2.join()
        }
        targetSocket.close()
    }

    override fun close() {
        serverJob.cancel()
    }
}

interface Socks5AuthProvider {
    suspend fun authenticate(username: String, password: String): Boolean
}
