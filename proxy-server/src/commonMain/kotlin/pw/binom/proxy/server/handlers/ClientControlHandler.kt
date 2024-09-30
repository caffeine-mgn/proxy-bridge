package pw.binom.proxy.server.handlers

import pw.binom.concurrency.SpinLock
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.io.httpServer.acceptWebsocket
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.metric.MetricProvider
import pw.binom.metric.MetricProviderImpl
import pw.binom.metric.MetricUnit
import pw.binom.network.NetworkManager
import pw.binom.proxy.server.ClientService
import pw.binom.proxy.server.properties.RuntimeClientProperties
import pw.binom.proxy.server.services.GatewayClientService
import pw.binom.proxy.server.services.ServerControlService
import pw.binom.strong.Strong
import pw.binom.strong.inject

class ClientControlHandler : HttpHandler, MetricProvider, Strong.DestroyableBean {
    //    private val clientService by inject<ClientService>()
//    private val networkManager by inject<NetworkManager>()
//    private val properties by inject<RuntimeClientProperties>()
//    private val controlService by inject<ServerControlService>()
    private val gatewayClientService by inject<GatewayClientService>()
    private val logger by Logger.ofThisOrGlobal
    private var clientCounter = 0
    private val clients = HashSet<WebSocketConnection>()
    private val clientsLock = SpinLock()
    private val metricProvider = MetricProviderImpl()
    override val metrics: List<MetricUnit> by metricProvider
    private val controlConnectionCounter = metricProvider.gaugeLong(name = "ws_control")

    override suspend fun handle(exchange: HttpServerExchange) {
        logger.info("Income control connection")
        val connection = exchange.acceptWebsocket()
        gatewayClientService.controlProcessing(connection)
        return
        /*
        val clientId = ++clientCounter
        val client =
            ControlClient(
                connection = connection,
                networkManager = networkManager,
                pingInterval = properties.pingInterval,
                logger = Logger.getLogger("External client $clientId")
            )
        try {
            controlConnectionCounter.inc()
            clientService.clientConnected(client)
            logger.info("Client $clientId connected!")
            clientsLock.synchronize {
                clients += connection
            }
            client.runClient(ControlClient.BaseHandler.NOT_SUPPORTED)
//            client.asyncCloseAnyway()
//            client.connection.asyncCloseAnyway()
        } catch (e: Throwable) {
            logger.info(text = "Client $clientId disconnected with error", exception = e)
        } finally {
            clientsLock.synchronize {
                clients -= connection
            }
            logger.info("Client $clientId disconnected!")
            withContext(NonCancellable) {
                clientService.clientDisconnected(client)
                client.asyncCloseAnyway()
            }
            controlConnectionCounter.dec()
        }
        */
    }

    override suspend fun destroy(strong: Strong) {
        ArrayList(clients).forEach {
            it.asyncCloseAnyway()
        }
    }
}
