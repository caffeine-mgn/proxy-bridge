package pw.binom.bluetooth

import com.github.hypfvieh.bluetooth.DeviceManager
import io.klogging.noCoLogger
import pw.binom.io.BluetoothConnection
import java.util.*

actual interface BluetoothAdapter {
    actual companion object {
        private val logger = noCoLogger<BluetoothAdapter>()
        actual fun getAdapters(): List<BluetoothAdapter> {
            val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
            val osArch = System.getProperty("os.arch").lowercase(Locale.getDefault())
            val isLinux = osName.contains("linux")
            val isArm = osArch.contains("arm") || osArch.contains("aarch")
            logger.info { "Creating DBus adapter" }

            return if (isLinux && isArm){
                val deviceManager = DeviceManager.createInstance(false)
                deviceManager.adapters.map {
                    BluetoothAdapterDBus(it)
                }
            } else {
                listOf(BluetoothCoveAdapter)
            }
        }
    }

    actual fun getLocalAddress(): String
    actual fun listenSPP(): SPPServer
    actual suspend fun connectSPP(address: String): BluetoothConnection
}
