package pw.binom.proxy.client

import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.AsyncChannel
import pw.binom.io.http.Encoding
import pw.binom.io.http.Headers
import pw.binom.io.http.emptyHeaders
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.addHeader
import pw.binom.io.httpClient.connectTcp
import pw.binom.io.httpClient.connectWebSocket
import pw.binom.io.socket.NetworkAddress
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.network.NetworkManager
import pw.binom.network.tcpConnect
import pw.binom.proxy.ChannelBridge
import pw.binom.proxy.Urls
import pw.binom.proxy.io.AsyncInputViaWebSocketMessage
import pw.binom.strong.Strong
import pw.binom.strong.inject

class TransportService : Strong.DestroyableBean {
    private val httpClient by inject<HttpClient>()
    private val networkManager by inject<NetworkManager>()
    private val runtimeProperties by inject<RuntimeProperties>()
    private val lock = SpinLock()
    private val connections = HashSet<TransportClient>()
    private var closing = false
    private val logger by Logger.ofThisOrGlobal

    suspend fun connectTcpPool(id: Int, address: NetworkAddress) {
        require(!closing) { "Service is closing" }
        val socket = networkManager.tcpConnect(address = address.resolve())

        val transportUrlRead =
            runtimeProperties.url.addPath(Urls.TRANSPORT_LONG_POOLING_NODE_WRITE.toPath { id.toString() })
        val transportUrlWrite =
            runtimeProperties.url.addPath(Urls.TRANSPORT_LONG_POOLING_CLIENT_WRITE.toPath { id.toString() })

        logger.info("TransportUrl READ: $transportUrlRead")
        logger.info("TransportUrl WRITE: $transportUrlWrite")
        val readReqeust = httpClient.connect(
            uri = transportUrlRead,
            method = "POST",
        )
        logger.info("Getting response from remote node")
        val resp = readReqeust.getResponse()
        logger.info("Response headers:\n${resp.headers}")
        if (resp.responseCode != 200) {
            TODO()
        }
        logger.info("Response got. Try connect second channel")

        val writeReqeust = httpClient.connect(
            uri = transportUrlWrite,
            method = "POST",
        ).addHeader(Headers.TRANSFER_ENCODING, Encoding.CHUNKED)
        logger.info("Start transport")
        ChannelBridge.create(
            local = socket,
            remote = AsyncChannel.create(
                input = resp.readData(),
                output = writeReqeust.writeData(),
            ),
            logger = Logger.getLogger("Client Transport #$id $address"),
            localName = "client",
            id = id,
        )
//        val transportClient = TransportTcpClient.start(
//            socket = socket,
//            transportConnection = AsyncChannel.create(
//                input = resp.readData(),
//                output = writeReqeust.writeData(),
//            ) {
//                writeReqeust.asyncCloseAnyway()
//                resp.asyncCloseAnyway()
//            },
//            logger = Logger.getLogger("Transport #$id $address"),
//            bufferSize = runtimeProperties.bufferSize,
//        ) {
//            removeClient(it)
//        }
//        lock.synchronize {
//            connections += transportClient
//        }
    }

    suspend fun connectWs(id: Int, address: NetworkAddress) {
        require(!closing) { "Service is closing" }
        val socket = networkManager.tcpConnect(address = address.resolve())
        val transportConnection = try {
            val transportUrl = runtimeProperties.url.addPath(Urls.TRANSPORT_WS.toPath { id.toString() })
            logger.info("TransportUrl: $transportUrl")
            httpClient.connectWebSocket(
                uri = transportUrl,
                masking = runtimeProperties.wsMasking,
            ).start()
        } catch (e: Throwable) {
            socket.asyncCloseAnyway()
            throw e
        }

        ChannelBridge.create(
            local = socket,
            remote = AsyncInputViaWebSocketMessage(transportConnection),
            logger = Logger.getLogger("Transport #$id $address"),
            bufferSize = runtimeProperties.bufferSize,
            localName = "client",
            id = id,
        )

//        val transportClient = TransportTcpClient.start(
//            socket = socket,
//            transportConnection = transportConnection,
//            logger = Logger.getLogger("Transport #$id $address"),
//            bufferSize = runtimeProperties.bufferSize,
//        ) {
//            removeClient(it)
//        }
//        lock.synchronize {
//            connections += transportClient
//        }
    }

    suspend fun connectTcp(id: Int, address: NetworkAddress) {
        require(!closing) { "Service is closing" }
        val socket = networkManager.tcpConnect(address = address.resolve())
        val transportConnection = try {
            val transportUrl = runtimeProperties.url.addPath(Urls.TRANSPORT_TCP.toPath { id.toString() })
            logger.info("TransportUrl: $transportUrl")
            httpClient.connectTcp(
                uri = transportUrl,
                headers = emptyHeaders(),
            ).start()
        } catch (e: Throwable) {
            socket.asyncCloseAnyway()
            throw e
        }


        ChannelBridge.create(
            local = socket,
            remote = transportConnection,
            logger = Logger.getLogger("Transport #$id $address"),
            bufferSize = runtimeProperties.bufferSize,
            localName = "client",
            id = id,
        )
//        val transportClient = TransportTcpClient.start(
//            socket = socket,
//            transportConnection = transportConnection,
//            logger = Logger.getLogger("Transport #$id $address"),
//            bufferSize = runtimeProperties.bufferSize,
//        ) {
//            removeClient(it)
//        }
//        lock.synchronize {
//            connections += transportClient
//        }
    }

    suspend fun connect(id: Int, address: NetworkAddress, transportType: RuntimeProperties.TransportType) {
        when (transportType) {
            RuntimeProperties.TransportType.TCP_OVER_HTTP -> connectTcp(
                id = id,
                address = address,
            )

            RuntimeProperties.TransportType.WS -> connectWs(
                id = id,
                address = address,
            )

            RuntimeProperties.TransportType.HTTP_POOLING -> connectTcpPool(
                id = id,
                address = address,
            )
        }
    }

    /*
        private fun removeClient(client: TransportTcpClient) {
            lock.synchronize {
                connections -= client
            }
        }
    */
    override suspend fun destroy(strong: Strong) {
        closing = true
        lock.synchronize { HashSet(connections) }.forEach {
            it.asyncCloseAnyway()
        }
    }
}
