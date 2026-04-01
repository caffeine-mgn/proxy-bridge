package pw.binom.proxy

import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice
import io.ktor.http.HttpStatusCode
import io.ktor.network.selector.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.*
import pw.binom.bluetooth.BluetoothAdapter
import pw.binom.channel.TcpConnectChannel
import pw.binom.dbus.SPP_UUID
import pw.binom.http.HttpProxy
import pw.binom.multiplexer.DuplexChannel
import pw.binom.multiplexer.Multiplexer
import pw.binom.utils.connect
import kotlin.time.Duration.Companion.seconds

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

val bluetoothAddress = "10:5F:AD:ED:55:16" //рабочий бук
//val bluetoothAddress = "00:1A:7D:DA:71:11" //мой компьютер

object MainJvm {
    @JvmStatic
    @JvmName("main")
    fun mainJvm(args: Array<String>) {

        var connected = false
        embeddedServer(io.ktor.server.cio.CIO, port = 8080) {
            routing {
                get("/health") {
                    if (connected) {
                        call.respondText(text = "Connected", status = HttpStatusCode.OK)
                    } else {
                        call.respondText(text = "Not connected", status = HttpStatusCode.ServiceUnavailable)
                    }
                }
            }
        }.start(wait = false)

        val adapter = BluetoothAdapter.getAdapters().first()
        SelectorManager(Dispatchers.IO).use { selector ->
            var currentMultiplexer: Multiplexer? = null
            val proxy = HttpProxy(
                port = 8077,
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
                    println("Connect to $bluetoothAddress")
                    val spp = try {
                        adapter.connectSPP(bluetoothAddress)
                    } catch (e: Throwable) {
                        connected = false
                        println("Can't connect to device: ${e.message}")
                        delay(5.seconds)
                        continue
                    }
                    println("Connected!")
                    try {
                        connected = true
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
                        connected = false
                        println("Error on processing: ${e.message}")
                        delay(5.seconds)
                        continue
                    }
                }
            }
        }
    }
}

