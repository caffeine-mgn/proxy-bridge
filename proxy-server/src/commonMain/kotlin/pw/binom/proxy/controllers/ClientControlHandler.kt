package pw.binom.proxy.controllers

import pw.binom.WebSocketProcessing
import pw.binom.concurrency.SpinLock
import pw.binom.io.http.forEachHeader
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.io.httpServer.acceptWebsocket
import pw.binom.io.useAsync
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.metric.MetricProvider
import pw.binom.metric.MetricProviderImpl
import pw.binom.metric.MetricUnit
import pw.binom.network.NetworkManager
import pw.binom.properties.PingProperties
import pw.binom.services.VirtualChannelService
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.Strong
import pw.binom.strong.inject

class ClientControlHandler : HttpHandler, MetricProvider {
    //    private val clientService by inject<ClientService>()
    private val networkManager by inject<NetworkManager>()

    //    private val properties by inject<RuntimeClientProperties>()
//    private val controlService by inject<ServerControlService>()
//    private val gatewayClientService by inject<GatewayClientService>()
    private val virtualChannelService by inject<VirtualChannelService>()
    private val logger by Logger.ofThisOrGlobal
    private var clientCounter = 0
    private val clients = HashSet<WebSocketConnection>()
    private val pingProperties by inject<PingProperties>()
    private val clientsLock = SpinLock()
    private val metricProvider = MetricProviderImpl()
    override val metrics: List<MetricUnit> by metricProvider
    private val controlConnectionCounter = metricProvider.gaugeLong(name = "ws_control")

    override suspend fun handle(exchange: HttpServerExchange) {
//        logger.info("Income control connection")
//        logger.info("Headers:")
//        exchange.requestHeaders.forEachHeader { key, value ->
//            logger.info("    $key: $value")
//        }

        logger.info("Income connect")
        val connection = exchange.acceptWebsocket()
        try {
            WebSocketProcessing(
                connection = connection,
                income = virtualChannelService.income,
                outcome = virtualChannelService.outcome,
                pingProperties = pingProperties,
            ).useAsync {
                logger.info("Start processing")
                it.processing(networkManager)
            }
        } finally {
            logger.info("Processing finished!")
        }
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

    init {
        BeanLifeCycle.preDestroy {
            ArrayList(clients).forEach {
                it.asyncCloseAnyway()
            }
        }
    }
}
