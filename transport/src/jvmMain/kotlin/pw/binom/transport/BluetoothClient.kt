package pw.binom.transport

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import pw.binom.transport.InputStreamImpl
import pw.binom.transport.io.AsyncInputStreamAdapter
import pw.binom.transport.io.AsyncOutputStreamAdapter
import javax.bluetooth.LocalDevice
import javax.microedition.io.Connector
import javax.microedition.io.StreamConnection
import kotlin.time.Duration.Companion.seconds


object BluetoothClient {
//    const val BUFFER_SIZE = 1011
    const val BUFFER_SIZE = 1002
    val SERVICE_UUID = javax.bluetooth.UUID("8ea95a10dd4f0a797f33bfd1dae14d97", false)
    fun start(
        remoteAddress: String,
        serviceUuid: javax.bluetooth.UUID = SERVICE_UUID,//javax.bluetooth.UUID(0x1101),
        serviceName: String = "SPPServer",
    ): VirtualManager {
        val workPc = RemoteDeviceImpl(remoteAddress)
        val uuidSet = arrayOf(serviceUuid) // UUID для Serial Port Profile (SPP)
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
            throw IllegalStateException("Can't get service for device $remoteAddress")
        }

        val sspUrl = services.find { it.type == serviceName }?.url
        if (sspUrl == null) {
            throw IllegalStateException("Can't find SSP on device $remoteAddress")
        }

        val connection = Connector.open(sspUrl) as StreamConnection
        val input = connection.openInputStream()
        val output = connection.openOutputStream()

        val asyncInput = AsyncInputStreamAdapter(
            inputStream = InputStreamImpl(input),
            bufferSize = BUFFER_SIZE,
        )
        val asyncOutput = AsyncOutputStreamAdapter(
            outputStream = OutputStreamImpl(output),
            bufferSize = BUFFER_SIZE,
        )
        return Manager.create(
            input = asyncInput,
            output = asyncOutput,
            maxPackageSize = BUFFER_SIZE,
            isServer = false,
        ).onClose {
            println("BluetoothClien::start closing...")
            input.close()
            output.close()
            asyncInput.free()
            asyncOutput.free()
            connection.close()
        }
    }
}
