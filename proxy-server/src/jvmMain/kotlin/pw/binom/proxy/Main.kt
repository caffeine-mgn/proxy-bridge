package pw.binom.proxy

import com.github.hypfvieh.bluetooth.DeviceManager
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import pw.binom.*
import pw.binom.bluetooth.BluetoothAdapter
import pw.binom.dbus.SPP_UUID
import pw.binom.dbus.asBluetoothConnection
import pw.binom.dbus.connectRfcomm
import pw.binom.dbus.findSPPProfile
import pw.binom.dbus.getDevice
import pw.binom.multiplexer.DuplexChannel
import pw.binom.multiplexer.Multiplexer
import pw.binom.test.ThroughputTest
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private fun checkSppViaSdpBrowse(deviceAddress: String): Boolean {
    return try {
        val process = ProcessBuilder("sdptool", "browse", deviceAddress)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        "0000110100002000800000805f9b34fb"
        // Ищем SPP сервис в выводе sdptool
        output.contains("Serial Port", ignoreCase = true) ||
                output.contains(SPP_UUID, ignoreCase = true)
    } catch (e: Exception) {
        println("sdptool check failed: ${e.message}")
        false
    }
}

fun checkSppServiceAvailable(device: BluetoothDevice): Boolean {
    // Проверяем список доступных сервисов устройства
    val availableUuids = device.uuids
    if (availableUuids.isNullOrEmpty()) {
        // UUIDs может быть пустым до подключения - нужно выполнить поиск через SDP
        return checkSppViaSdpBrowse(device.address)
    }
    println("device.isServicesResolved=${device.isServicesResolved}")
    println("uuid:")
    availableUuids.forEach {
        println("->${it}")
    }
    // Ищем SPP UUID в списке доступных сервисов
    return availableUuids.any { uuid ->
        uuid.equals(SPP_UUID, ignoreCase = true) ||
                uuid.equals("00001101-0000-1000-8000-00805f9b34fb", ignoreCase = true) ||
                uuid.equals("1101", ignoreCase = true)  // короткая форма
    }
}


private const val SPP_UUID1 = "00001101-0000-1000-8000-00805F9B34FB"


suspend fun clientProcessing(channel: DuplexChannel){

}

object MainJvm {
    @JvmStatic
    @JvmName("main")
    fun mainJvm(args: Array<String>) {
        val adapter = BluetoothAdapter.getAdapters().first()
        runBlocking {
            adapter.connectSPP("00:1A:7D:DA:71:11").use { connection ->
                Multiplexer(
                    channel = connection,
                    idOdd = true,
                    ioCoroutineScope = CoroutineScope(Dispatchers.IO)
                ).use { multiplexer ->
                    while (isActive){
                        val newClient = multiplexer.accept()
                        CoroutineScope(Dispatchers.IO).launch {
                            clientProcessing(newClient)
                        }
                    }
                }
//                val list = listOf(100, 200, 500, 512, 1000, 1024, 2048, 4096)
//                val time = 5.seconds
//                list.forEach { size ->
//                    val v100 = ThroughputTest.client(connection, bodySize = size, time = 5.seconds, ack = true)
//                    println("$size-> ${v100.toDouble() / (time.inWholeMilliseconds * 0.001)}")
//                }
            }
        }
    }
}

suspend fun main(args: Array<String>) {
    val connected = BluetoothClient.connect("AAAAAAAAAAAA")
    connected.income.receive()
}
