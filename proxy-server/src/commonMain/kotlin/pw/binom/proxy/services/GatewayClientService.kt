package pw.binom.proxy.services

import kotlinx.coroutines.isActive
import pw.binom.atomic.AtomicBoolean
import pw.binom.gateway.GatewayClient
import pw.binom.gateway.GatewayClientWebSocket
import pw.binom.io.ClosedException
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.infoSync
import pw.binom.metric.MetricProvider
import pw.binom.metric.MetricProviderImpl
import pw.binom.metric.MetricUnit
import pw.binom.proxy.dto.ControlEventDto
import pw.binom.proxy.dto.ControlRequestDto
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.EventSystem
import pw.binom.strong.inject
import kotlin.coroutines.coroutineContext

class GatewayClientService : GatewayClient, MetricProvider {

    private var lastConnection: GatewayClient? = null
    private val eventSystem by inject<EventSystem>()
    private val logger by Logger.ofThisOrGlobal
    private val closing = AtomicBoolean(false)
    private val metricProvider = MetricProviderImpl()
    override val metrics: List<MetricUnit> by metricProvider
    private val hasActiveConnect = metricProvider.gaugeLong("active_gateway") {
        if (lastConnection != null) {
            1L
        } else {
            0L
        }
    }

    val isActive
        get() = lastConnection != null

    init {
        BeanLifeCycle.preDestroy {
            closing.setValue(true)
            lastConnection?.asyncCloseAnyway()
            lastConnection = null
        }
    }

    suspend fun controlProcessing(connection: WebSocketConnection) {
        if (closing.getValue()) {
            return
        }
        lastConnection?.asyncCloseAnyway()
        val connection = GatewayClientWebSocket(connection)
        lastConnection = connection
        try {
            while (coroutineContext.isActive && !closing.getValue()) {
                val event = try {
                    connection.receiveEvent()
                } catch (e: ClosedException) {
                    logger.info("Processing finished")
                    break
                }
                eventProcessing(event)
            }
        } catch (e: Throwable) {
            lastConnection?.asyncCloseAnyway()
            lastConnection = null
            logger.info("Error on control processing")
            throw e
        }
    }

    private suspend fun eventProcessing(eventDto: ControlEventDto) {
        logger.infoSync("Income event $eventDto")
        eventSystem.dispatch(eventDto)
    }

    private fun getClient() = lastConnection ?: throw IllegalStateException("No active client")

    override suspend fun sendCmd(request: ControlRequestDto) {
        logger.info("Send cmd $request")
        val client = getClient().sendCmd(request)
    }

    override suspend fun receiveEvent(): ControlEventDto {
        TODO("Not yet implemented")
    }

    override suspend fun asyncClose() {
        TODO("Not yet implemented")
    }
}
