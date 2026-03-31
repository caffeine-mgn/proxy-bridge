package pw.binom.proxy

import com.github.hypfvieh.bluetooth.DeviceManager
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.writeString
import pw.binom.*
import pw.binom.bluetooth.BluetoothAdapter
import pw.binom.channel.TcpConnectChannel
import pw.binom.dbus.SPP_UUID
import pw.binom.dbus.asBluetoothConnection
import pw.binom.dbus.connectRfcomm
import pw.binom.dbus.findSPPProfile
import pw.binom.dbus.getDevice
import pw.binom.http.HttpProxy
import pw.binom.multiplexer.DuplexChannel
import pw.binom.multiplexer.Multiplexer
import pw.binom.test.ThroughputTest
import pw.binom.utils.connect
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


suspend fun clientProcessing(channel: DuplexChannel) {
    val first = channel.income.receive()

}


object MainJvm {
    @JvmStatic
    @JvmName("main")
    fun mainJvm(args: Array<String>) {
        val adapter = BluetoothAdapter.getAdapters().first()
        SelectorManager(Dispatchers.IO).use { selector ->
            var currentMultiplexer: Multiplexer? = null
            val proxy = HttpProxy(
                port = 8088,
                selector = selector,
                onConnect = { host, port, context ->
                    val multiplexer = currentMultiplexer
                    if (multiplexer == null) {
                        context.ioError()
                        return@HttpProxy
                    }
                    val channel = TcpConnectChannel.connect(
                        host = host,
                        port = port,
                        multiplexer = multiplexer
                    )
                    if (channel == null) {
                        context.notAvailable()
                        return@HttpProxy
                    }
                    val (read, write) = try {
                        context.ok()
                    } catch (_: Throwable) {
                        channel.cancel()
                        channel.close()
                        return@HttpProxy
                    }
                    connect(
                        outcome = channel.outcome,
                        income = channel.income,
                        a = write,
                        b = read,
                    )
                },
                onHttp = { _, _, _, _ -> }
            )

            runBlocking {
                while (isActive) {
                    val spp = try {
                        adapter.connectSPP("00:1A:7D:DA:71:11")
                    } catch (e: Throwable) {
                        println("Can't connect to device: ${e.message}")
                        delay(5.seconds)
                        continue
                    }
                    try {
                        spp.use { connection ->
                            Multiplexer(
                                channel = connection,
                                idOdd = true,
                                ioCoroutineScope = CoroutineScope(Dispatchers.IO)
                            ).use { multiplexer ->
                                currentMultiplexer = multiplexer
                                while (isActive) {
                                    val newClient = multiplexer.accept()
                                    CoroutineScope(Dispatchers.IO).launch {
                                        clientProcessing(newClient)
                                    }
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        currentMultiplexer = null
                        println("Error on processing: ${e.message}")
                        delay(5.seconds)
                        continue
                    }
                }
            }
        }
    }
}

suspend fun main(args: Array<String>) {
    val connected = BluetoothClient.connect("AAAAAAAAAAAA")
    connected.income.receive()
}
