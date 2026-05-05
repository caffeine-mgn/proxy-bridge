package pw.binom.proxy

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice
import io.github.oshai.kotlinlogging.KotlinLogging
import io.klogging.Level
import io.klogging.config.loggingConfiguration
import io.ktor.http.HttpStatusCode
import io.ktor.network.selector.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.*
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose
import pw.binom.ConnectionAcceptor
import pw.binom.channel.FileChannel
import pw.binom.channel.TcpConnectChannel
import pw.binom.com.comSerialKoinModule
import pw.binom.dbus.SPP_UUID
import pw.binom.http.HttpProxy
import pw.binom.io.SelectorManagerKoinModule
import pw.binom.multiplexer.DuplexChannel
import pw.binom.multiplexer.Multiplexer
import pw.binom.multiplexer.MultiplexerHolder
import pw.binom.multiplexer.MultiplexerImpl
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

object SerialPortSerevrCommand : CliktCommand() {
    val port by option("-p", "--port").required()
    override fun run() {
        TODO("Not yet implemented")
    }

}

private val logger = KotlinLogging.logger("GLOBAL")

object MainJvm {
    @JvmStatic
    @JvmName("main")
    fun mainJvm(args: Array<String>) {
        logger.info { "STARTUP" }
        val koin = startKoin {
            modules(
                comSerialKoinModule(
                    serialName = lazyOf("/dev/ttyGS0")
                ),
                SelectorManagerKoinModule,
                module {
                    single { MultiplexerHolder() } bind Multiplexer::class
                },
                module {
                    single(createdAtStart = true) { FileServer() } onClose { it?.close() }
                },
                FileChannel.module,
                TcpConnectChannel.module,
            )
        }
        val con by koin.koin.inject<ConnectionAcceptor>()
        val selector by koin.koin.inject<SelectorManager>()
        val multiplexerHolder by koin.koin.inject<MultiplexerHolder>()
//        val con: ConnectionAcceptor = SerialConnectionAcceptor("/dev/ttyGS0")
//        val con:ConnectionAcceptor = BluetoothClientConnectionAcceptor(
//            adapter = BluetoothAdapter.getAdapters().first(),
//            bluetoothAddress = bluetoothAddress,
//        )
        loggingConfiguration {
            this.kloggingMinLogLevel(Level.DEBUG)
        }
        var connected = false
        embeddedServer(io.ktor.server.cio.CIO, port = 8076) {
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
        var currentMultiplexer: MultiplexerImpl? = null
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
                try {
                    connect(
                        outcome = channel.outcome,
                        income = channel.income,
                        a = write,
                        b = read,
                    )
                } catch (_: Throwable) {
                    channel.cancel()
                    channel.close()
                }
            },
            onHttp = { _, _, _, _ -> }
        )
        runBlocking {
            while (isActive) {
                val spp = try {
                    println("123")
                    con.connection()
                } catch (e: Throwable) {
                    connected = false
                    println("Can't connect to gateway: ${e.message}")
                    delay(5.seconds)
                    continue
                }
                println("Connected!")
                try {
                    connected = true
                    runBlocking {
                        spp.use { connection ->
                            MultiplexerImpl(
                                channel = connection,
                                idOdd = true,
                                ioCoroutineScope = CoroutineScope(Dispatchers.IO)
                            ).use { multiplexer ->
                                multiplexerHolder.use(multiplexer) {
                                    try {
                                        currentMultiplexer = multiplexer
                                        while (isActive) {
                                            println("Try accept....")
                                            val newClient = multiplexer.accept()
                                            CoroutineScope(Dispatchers.IO).launch {
                                                clientProcessing(newClient)
                                            }
                                        }
                                        println("PROCESSING FINISHED!")
                                    } catch (e: Throwable) {
                                        e.printStackTrace()
                                    }
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

