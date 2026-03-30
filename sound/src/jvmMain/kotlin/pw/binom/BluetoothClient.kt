package pw.binom

import kotlinx.coroutines.*
import pw.binom.bluetooth.ConnectorAsync
import pw.binom.bluetooth.asyncSearchServices
import pw.binom.bluetooth.create
import pw.binom.bluetooth.type
import pw.binom.bluetooth.url
import pw.binom.io.BluetoothConnection
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.infoSync
import pw.binom.logger.warn
import javax.bluetooth.LocalDevice
import javax.microedition.io.StreamConnection
import kotlin.time.Duration

actual class BluetoothClient actual constructor(
    private val addressAndChannel: String,
    private val reconnectDelay: Duration,
) : AutoCloseable {
    actual companion object {
        actual suspend fun connect(addressAndChannel: String): BluetoothConnection {
            val localDevice = LocalDevice.getLocalDevice()
            val attrIDs = intArrayOf(0x0100) // Атрибуты сервиса
            val uuidSet = arrayOf(javax.bluetooth.UUID(0x1101)) // UUID для Serial Port Profile (SPP)
            val workPc = RemoteDeviceImpl(addressAndChannel)
            val services = localDevice.discoveryAgent.asyncSearchServices(
                attrSet = attrIDs,
                uuidSet = uuidSet,
                device = workPc,
            )
            println("services->${services}")
            val sspUrl = services.find { it.type == SPPServerName }?.url
                ?: throw IllegalArgumentException("Can't find SSP on device $addressAndChannel")
            return withContext(Dispatchers.IO) {
                val connection = ConnectorAsync.open(sspUrl) as StreamConnection
                BluetoothConnection.create(connection)
            }
        }
    }

    private val logger by Logger.ofThisOrGlobal
    private var streamConnection: StreamConnection? = null
    private var closing = false
    private var clientConnection: BluetoothConnection? = null

    suspend fun connect() {
        val workPc = RemoteDeviceImpl(addressAndChannel)
        val uuidSet = arrayOf(javax.bluetooth.UUID(0x1101)) // UUID для Serial Port Profile (SPP)
        val attrIDs = intArrayOf(0x0100) // Атрибуты сервиса
        val localDevice = LocalDevice.getLocalDevice()
        println("bluetoothAddress: ${localDevice.bluetoothAddress}")
        val services = localDevice.discoveryAgent.asyncSearchServices(
            attrSet = attrIDs,
            uuidSet = uuidSet,
            device = workPc,
        )
        val sspUrl = services.find { it.type == SPPServerName }?.url
            ?: throw IllegalArgumentException("Can't find SSP on device $addressAndChannel")

        withContext(Dispatchers.IO) {
            logger.info("Connecting to $sspUrl")
            val connection = ConnectorAsync.open(sspUrl) as StreamConnection
            streamConnection = connection
            logger.info("Connected to $sspUrl")
            try {

                val channel = BluetoothConnection.create(connection)
                this@BluetoothClient.clientConnection = null
            } catch (e: Throwable) {
                logger.warn(text = "Error on processing client. Wait $reconnectDelay", exception = e)
                delay(reconnectDelay)
            } finally {
                streamConnection?.close()
            }
        }
    }

    private val job = CoroutineScope(Dispatchers.IO).launch {
        while (isActive) {
            TODO()
        }
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    init {
        TODO()
        logger.infoSync("Init service")
//        BeanLifeCycle.preDestroy {
//            try {
//                logger.info("Stopping service")
//                closing = true
//                logger.info("#1")
//                streamConnection?.close()
//                logger.info("#3")
//                clientConnection?.close()
//                logger.info("#3.5")
//                job?.cancelAndJoin()
//                logger.info("#5")
//                logger.info("Service stopped111")
//            } finally {
//                logger.info("Service stopped")
//            }
//        }
//        BeanLifeCycle.postConstruct {
//            job = GlobalScope.launch {
//                while (isActive && !closing) {
//
//                }
//            }
//        }
    }
}
