package pw.binom.transport

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.logging.Logger
import javax.bluetooth.LocalDevice
import javax.microedition.io.Connector
import javax.microedition.io.StreamConnection
import kotlin.time.Duration.Companion.seconds

object BootstrapBluetoothClient {
    private const val addressAndChannel = "105FADED5516"
    private val logger = Logger.getLogger(this::class.java.name)
    const val SPPServerName = "SPPServer"
    fun start() {
        val file = File("/home/subochev/projects/WORK/proxy-bridge/transport/build/libs/transport.jar")
        while (!Thread.currentThread().isInterrupted) {
            val workPc = RemoteDeviceImpl(addressAndChannel)
            val uuidSet = arrayOf(javax.bluetooth.UUID(0x1101)) // UUID для Serial Port Profile (SPP)
            val attrIDs = intArrayOf(0x0100) // Атрибуты сервиса
            val localDevice = LocalDevice.getLocalDevice()
            val services = runBlocking {
                withTimeoutOrNull(5.seconds) {
                    localDevice.discoveryAgent.asyncSearchServices(
                        attrSet = attrIDs,
                        uuidSet = uuidSet,
                        device = workPc,
                    )
                }
            }

            if (services == null) {
                logger.info("Can't get service for device $addressAndChannel")
                Thread.sleep(5.seconds.inWholeMilliseconds)
                continue
            }

            val sspUrl = services.find { it.type == SPPServerName }?.url
            if (sspUrl == null) {
                logger.info("Can't find SSP on device $addressAndChannel")
                Thread.sleep(5.seconds.inWholeMilliseconds)
                continue
            }
            val connection = Connector.open(sspUrl) as StreamConnection
            connection.openInputStream().use { inputStream ->
                connection.openOutputStream().use { outputStream ->
                    outputStream.write(Codes.START_FILE.toInt())
                    outputStream.writeString(file.name)
                    outputStream.writeInt(file.length().toInt())
                    file.inputStream().use { input ->
                        input.copyTo2(outputStream) { bytesCopied ->
                            val percent = (bytesCopied.toDouble()) / (file.length().toDouble()) * 100.0
                            println("Progress $percent%")
                        }
                    }
                }
            }
            connection.close()
            break
        }
    }
}
