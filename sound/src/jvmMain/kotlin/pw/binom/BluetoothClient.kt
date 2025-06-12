package pw.binom

import kotlinx.coroutines.*
import pw.binom.bluetooth.ConnectorAsync
import pw.binom.bluetooth.asyncSearchServices
import pw.binom.bluetooth.type
import pw.binom.bluetooth.url
import pw.binom.io.useAsync
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.infoSync
import pw.binom.logger.warn
import pw.binom.services.VirtualChannelService
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.inject
import javax.bluetooth.LocalDevice
import javax.microedition.io.ConnectionNotFoundException
import javax.microedition.io.StreamConnection
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

actual class BluetoothClient actual constructor(
    private val addressAndChannel: String,
    private val reconnectDelay: Duration,
) {
    private val virtualChannelService by inject<VirtualChannelService>()
    private var job: Job? = null
    private val logger by Logger.ofThisOrGlobal
    private var streamConnection: StreamConnection? = null
    private var closing = false
    private var clientConnection: BluetoothConnection? = null

    init {
        logger.infoSync("Init service")
        BeanLifeCycle.preDestroy {
            try {
                logger.info("Stopping service")
                closing = true
                logger.info("#1")
                streamConnection?.close()
                logger.info("#3")
                clientConnection?.asyncClose()
                logger.info("#3.5")
                job?.cancelAndJoin()
                logger.info("#5")
                logger.info("Service stopped111")
            } finally {
                logger.info("Service stopped")
            }
        }
        BeanLifeCycle.postConstruct {
            job = GlobalScope.launch {
                while (isActive && !closing) {
                    val workPc = RemoteDeviceImpl(addressAndChannel)
                    val uuidSet = arrayOf(javax.bluetooth.UUID(0x1101)) // UUID для Serial Port Profile (SPP)
                    val attrIDs = intArrayOf(0x0100) // Атрибуты сервиса
                    val localDevice = LocalDevice.getLocalDevice()
                    val services = withTimeoutOrNull(5.seconds) {
                        localDevice.discoveryAgent.asyncSearchServices(
                            attrSet = attrIDs,
                            uuidSet = uuidSet,
                            device = workPc,
                        )
                    }
                    if (services == null) {
                        logger.info("Can't get service for device $addressAndChannel")
                        delay(reconnectDelay)
                        continue
                    }
                    val sspUrl = services.find { it.type == SPPServerName }?.url
                    if (sspUrl == null) {
                        logger.info("Can't find SSP on device $addressAndChannel")
                        delay(reconnectDelay)
                        continue
                    }

                    logger.info("Connecting to $sspUrl")
                    val connection = try {
                        withTimeoutOrNull(5.seconds) {
                            ConnectorAsync.open(sspUrl) as StreamConnection
                        }

                    } catch (e: ConnectionNotFoundException) {
                        logger.warn("Device \"$addressAndChannel\" not found. Wait $reconnectDelay")
                        delay(reconnectDelay)
                        continue
                    } catch (e: Throwable) {
                        logger.warn("Can't connect to \"$addressAndChannel\". Wait $reconnectDelay")
                        delay(reconnectDelay)
                        continue
                    }
                    if (connection==null){
                        logger.warn(text = "Timeout connection to $sspUrl. Wait $reconnectDelay")
                        delay(reconnectDelay)
                        continue
                    }
                    streamConnection = connection
                    logger.info("Connected to $sspUrl")
                    try {
                        val channel = BluetoothAsyncChannel(connection)
                        BluetoothConnection(
                            income = virtualChannelService.income,
                            outcome = virtualChannelService.outcome,
                            nativeChannel = channel,
                        ).useAsync { clientConnection ->
                            this@BluetoothClient.clientConnection = clientConnection
                            clientConnection.processing()
                        }
                        this@BluetoothClient.clientConnection = null
                    } catch (e: Throwable) {
                        logger.warn(text = "Error on processing client. Wait $reconnectDelay", exception = e)
                        delay(reconnectDelay)
                        continue
                    } finally {
                        streamConnection?.close()
                    }
                }
            }
        }
    }
}
