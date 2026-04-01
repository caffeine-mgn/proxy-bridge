package pw.binom.dbus

import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

suspend fun BluetoothDevice.awaitUntilConnected(checkInterval: Duration = 1.seconds) {
    withContext(Dispatchers.IO) {
        while (isActive) {
            if (isConnected) {
                break
            }
            delay(checkInterval)
        }
    }
}

suspend fun BluetoothDevice.awaitUntilDisconnected(checkInterval: Duration = 1.seconds) {
    withContext(Dispatchers.IO) {
        while (isActive) {
            if (!isConnected) {
                break
            }
            delay(checkInterval)
        }
    }
}

fun BluetoothDevice.connectRfcomm(): Rfcomm {
    val number = (0..255).firstOrNull { n ->
        val rfcommDevice = File("/dev/rfcomm$n")
        !rfcommDevice.exists()
    } ?: throw IllegalStateException("No available RFCOMM devices (0-255 all in use)")
    val rawPath = "/dev/rfcomm$number"
    println("Binding $rawPath....")
    val args2 = listOf("rfcomm", "-i", adapter.deviceName, "bind", number.toString(), address, "4")
    val builder2 = ProcessBuilder(*args2.toTypedArray())
    builder2.redirectOutput(ProcessBuilder.Redirect.INHERIT)
    builder2.redirectError(ProcessBuilder.Redirect.INHERIT)
    val exitCode2 =
        builder2.start().waitFor()
    check(exitCode2 == 0) { "Can't bind rfcomm: invalid exit code: $exitCode2" }
    check(File(rawPath).exists()) { "Can't bind: file $rawPath not found" }
    Rfcomm.resetMode(number)
    Rfcomm.disableEcho(number)
    println("Bound!")
    return Rfcomm(
        number = number,
        device = this,
    )
}

fun BluetoothDevice.findSPPProfile() = uuids.filter { uuid ->
    uuid.equals("00001101-0000-1000-8000-00805f9b34fb", ignoreCase = true) ||
            uuid.equals("00001101-0000-1000-8000-00805f9b34fb", ignoreCase = true) ||
            uuid.equals("1101", ignoreCase = true)
}
