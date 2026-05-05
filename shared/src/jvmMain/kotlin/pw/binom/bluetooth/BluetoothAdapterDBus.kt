package pw.binom.bluetooth

import pw.binom.dbus.asBluetoothConnection
import pw.binom.dbus.connectRfcomm
import pw.binom.dbus.getDevice
import pw.binom.io.BluetoothConnection
import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter as DBusBluetoothAdapter

class BluetoothAdapterDBus(val adapter: DBusBluetoothAdapter) : BluetoothAdapter {
    override fun getLocalAddress(): String = adapter.address

    override fun listenSPP(): SPPServer {
        TODO("Not yet implemented")
    }

    override suspend fun connectSPP(address: String, onClose: () -> Unit): BluetoothConnection {
        val remoteDevice = adapter.getDevice(address)
        return remoteDevice.connectRfcomm().asBluetoothConnection(onClose)
    }
}
