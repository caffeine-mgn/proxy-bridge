package pw.binom.bluetooth

import io.klogging.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pw.binom.io.BluetoothConnection
import javax.bluetooth.LocalDevice
import javax.bluetooth.RemoteDevice
import javax.microedition.io.StreamConnection

object BluetoothCoveAdapter : BluetoothAdapter {
    private val device = LocalDevice.getLocalDevice()
    override fun getLocalAddress(): String = device.bluetoothAddress

    override fun listenSPP(): SPPServer = SPPServerCove()

    private class RemoteDeviceImpl(address: String) : RemoteDevice(address)

    private const val SPPServerName = "SPPServer"
    private val logger = logger(this::class)

    override suspend fun connectSPP(address: String): BluetoothConnection {
        logger.info { "Connecting to $address..." }
        val localDevice = LocalDevice.getLocalDevice()
        val attrIDs = intArrayOf(0x0100) // Атрибуты сервиса
        val uuidSet = arrayOf(javax.bluetooth.UUID(0x1101)) // UUID для Serial Port Profile (SPP)
        val workPc = RemoteDeviceImpl(address.replace(":", ""))
        val services = localDevice.discoveryAgent.asyncSearchServices(
            attrSet = attrIDs,
            uuidSet = uuidSet,
            device = workPc,
        )
        println("services->${services}")

        val sspUrl = "btspp://${address.replace(":","")}:4;authenticate=false;encrypt=false;master=false"
//        val sspUrl = services.find { it.type == SPPServerName }?.url
//            ?: throw IllegalArgumentException("Can't find SSP on device $address")
        return withContext(Dispatchers.IO) {
            val connection = ConnectorAsync.open(sspUrl) as StreamConnection
            BluetoothConnection.create(connection)
        }
    }

}
