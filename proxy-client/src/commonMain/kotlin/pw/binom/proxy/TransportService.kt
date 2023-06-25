package pw.binom.proxy

import kotlinx.coroutines.supervisorScope
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.http.emptyHeaders
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.connectTcp
import pw.binom.io.httpClient.connectWebSocket
import pw.binom.io.socket.NetworkAddress
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.network.NetworkManager
import pw.binom.network.tcpConnect
import pw.binom.strong.Strong
import pw.binom.strong.inject
import pw.binom.url.toPath

class TransportService : Strong.DestroyableBean {
    private val httpClient by inject<HttpClient>()
    private val networkManager by inject<NetworkManager>()
    private val runtimeProperties by inject<RuntimeProperties>()
    private val lock = SpinLock()
    private val connections = HashSet<TransportTcpClient>()
    private var closing = false
    private val logger by Logger.ofThisOrGlobal
    suspend fun connectTcp(id: Int, host: String, port: Int) {
        require(!closing) { "Service is closing" }
        val socket = networkManager.tcpConnect(
            address = NetworkAddress.create(
                host = host,
                port = port,
            )
        )
        val transportConnection = try {
            val wsShema = when (runtimeProperties.url.schema) {
                "http" -> "ws"
                "https" -> "wss"
                else -> error("Invalid schema: ${runtimeProperties.url.schema}")
            }
            val transportUrl = runtimeProperties
                .url
                .copy(schema = wsShema)
                .addPath(Urls.TRANSPORT.toPath)
                .appendQuery("id", id.toString())
            logger.info("TransportUrl: $transportUrl")
            httpClient.connectTcp(
                uri =transportUrl ,
                headers = emptyHeaders(),
            ).start()
//            httpClient.connectWebSocket(
//                uri = transportUrl,
//                masking = false,
//            ).start()
        } catch (e: Throwable) {
            socket.close()
            throw e
        }

        val transportClient = TransportTcpClient.start(
            socket = socket,
            transportConnection = transportConnection,
            logger = Logger.getLogger("Transport #$id $host:$port")
        ) {
            removeClient(it)
        }
        lock.synchronize {
            connections += transportClient
        }
    }

    private fun removeClient(client: TransportTcpClient) {
        lock.synchronize {
            connections -= client
        }
    }

    override suspend fun destroy(strong: Strong) {
        closing = true
        lock.synchronize { HashSet(connections) }.forEach {
            it.asyncCloseAnyway()
        }
    }
}