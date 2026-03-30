package pw.binom.dbus

import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice
import org.bluez.Device1

fun BluetoothAdapter.getDevice(address: String): BluetoothDevice {
    // Формируем путь к устройству: /org/bluez/hci0/dev_XX_XX_XX_XX_XX_XX
    val devicePath = "${dbusPath}/dev_${address.replace(":", "_")}"
    val deivce = dbusConnection.getRemoteObject("org.bluez", devicePath, Device1::class.java)
    return BluetoothDevice(
        deivce,
        this,
        devicePath,
        dbusConnection
    )
}
