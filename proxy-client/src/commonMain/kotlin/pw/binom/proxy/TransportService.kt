package pw.binom.proxy

import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.http.BasicAuth
import pw.binom.io.http.BearerAuth
import pw.binom.io.http.emptyHeaders
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.connectTcp
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
    suspend fun connectTcp(id: Int, address: NetworkAddress) {
        require(!closing) { "Service is closing" }
//        val socket = if (runtimeProperties.proxy != null) {
//            val auth = runtimeProperties.proxy?.proxyAuth?.basicAuth?.let {
//                BasicAuth(login = it.user, password = it.password)
//            } ?: runtimeProperties.proxy?.proxyAuth?.bearerAuth?.let {
//                BearerAuth(token = it.token)
//            }
//            networkManager.tcpConnectViaHttpProxy(
//                    proxy = runtimeProperties.proxy!!.address.resolve(),
//                    address = address,
//                    auth = auth,
//                    readBufferSize = runtimeProperties.bufferSize,
//            )
//        } else {
//            networkManager.tcpConnect(address = address.resolve())
//        }
        val socket = networkManager.tcpConnect(address = address.resolve())
        val transportConnection = try {
            val transportUrl = runtimeProperties.url.addPath(Urls.TRANSPORT.toPath).appendQuery("id", id.toString())
            logger.info("TransportUrl: $transportUrl")
            httpClient.connectTcp(
                    uri = transportUrl,
                    headers = emptyHeaders(),
            ).start()
        } catch (e: Throwable) {
            socket.asyncCloseAnyway()
            throw e
        }

        val transportClient = TransportTcpClient.start(
                socket = socket,
                transportConnection = transportConnection,
                logger = Logger.getLogger("Transport #$id $address"),
                bufferSize = runtimeProperties.bufferSize,
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