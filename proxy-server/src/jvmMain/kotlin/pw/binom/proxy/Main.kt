package pw.binom.proxy

import com.charleskorn.kaml.Yaml
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readText
import kotlinx.coroutines.*
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import org.koin.dsl.onClose
import pw.binom.*
import pw.binom.channel.FileChannel
import pw.binom.channel.TcpConnectChannel
import pw.binom.io.SelectorManagerKoinModule
import pw.binom.multiplexer.DuplexChannel
import pw.binom.multiplexer.Multiplexer
import pw.binom.multiplexer.MultiplexerHolder
import pw.binom.multiplexer.MultiplexerImpl
import pw.binom.properties.ConfigModule
import pw.binom.properties.Configuration
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.seconds
import kotlin.use


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
    @OptIn(ExperimentalAtomicApi::class)
    @JvmStatic
    @JvmName("main")
    fun mainJvm(args: Array<String>) {
        val configFile = Path("config.yaml")
        if (!SystemFileSystem.exists(configFile)) {
            println("Config file missing")
            return
        }
        val config = SystemFileSystem.source(configFile).buffered().use {
            Yaml.default.decodeFromString(Configuration.serializer(), it.readText())
        }
        println("config:\n${Yaml.default.encodeToString(Configuration.serializer(), config)}")
        logger.info { "STARTUP" }
        val koin = startKoin {
            modules(
                ConfigModule.createModule(config),
//                comSerialKoinModule(
//                    serialName = lazyOf("/dev/ttyGS0")
//                ),
//                module {
//                    single { TcpConnectProvider.Direct(get()) } bind TcpConnectProvider::class
//                },
                SelectorManagerKoinModule,
//                module {
//                    single { MultiplexerHolder() } binds (arrayOf(Multiplexer::class, MultiplexerHolder::class))
//                },
                module {
                    single(createdAtStart = true) { FileServer() } onClose { it?.close() }
                },
                module { single { TcpConnectChannel(get(), get(), get()) } },
                module {
                    single {
                        ConnectProcessingImpl(get())
                    }.bind(ConnectProcessing::class)
                },
                FileChannel.module,
                TcpConnectChannel.module,
//                HttpProxyModule(port = 8077),
//                Sock5ProxyModule(port = 1080),
//                WebDavServer.module(port=8075,rootDir= Paths.get("/tmp/web-dav-root"))
            )
        }
        val closed = AtomicBoolean(false)
        Runtime.getRuntime().addShutdownHook(Thread {
            closed.store(true)
        })
        while (!closed.load()) {
            Thread.sleep(1000)
        }
        return
        val con by koin.koin.inject<ConnectionAcceptor>()
//        val selector by koin.koin.inject<SelectorManager>()
        val multiplexerHolder by koin.koin.inject<MultiplexerHolder>()
//        val con: ConnectionAcceptor = SerialConnectionAcceptor("/dev/ttyGS0")
//        val con:ConnectionAcceptor = BluetoothClientConnectionAcceptor(
//            adapter = BluetoothAdapter.getAdapters().first(),
//            bluetoothAddress = bluetoothAddress,
//        )
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
                    connected = false
                    println("Error on processing: ${e.message}")
                    delay(5.seconds)
                    continue
                }
            }
        }
    }
}

