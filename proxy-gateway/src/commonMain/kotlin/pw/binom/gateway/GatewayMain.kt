package pw.binom.gateway

import com.charleskorn.kaml.Yaml
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.selector.*
import pw.binom.properties.Configuration
import io.ktor.utils.io.readText
import kotlinx.coroutines.*
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.koin.core.context.startKoin
import org.koin.dsl.module
import pw.binom.*
import pw.binom.channel.ChannelSelector
import pw.binom.channel.FileChannel
import pw.binom.channel.TcpConnectChannel
import pw.binom.com.comSerialKoinModule
import pw.binom.io.SelectorManagerKoinModule
import pw.binom.multiplexer.MultiplexerImpl
import pw.binom.properties.ConfigModule
import kotlin.coroutines.coroutineContext
import kotlin.use

private val logger = KotlinLogging.logger("GLOBAL")
suspend fun main(args: Array<String>) {
    logger.info { "STARTUP!" }

    val configFile = Path("config.yaml")
    if (!SystemFileSystem.exists(configFile)) {
        println("Config file missing")
        return
    }
    val config = SystemFileSystem.source(configFile).buffered().use {
        Yaml.default.decodeFromString(Configuration.serializer(), it.readText())
    }

    val koin = startKoin {
        modules(
            ConfigModule.createModule(config),
            comSerialKoinModule(
//                serialName = lazyOf("/dev/ttyACM0")
                serialName = lazyOf("COM4")
            ),
            SelectorManagerKoinModule,
            ChannelSelector.module,
            FileChannel.module,
            TcpConnectChannel.module,
            module {
                single { TcpConnectProvider.Direct(get()) }
            }
        )
    }
    val server: ConnectionAcceptor by koin.koin.inject<ConnectionAcceptor>()
    val selector by koin.koin.inject<SelectorManager>()
    val channelSelector by koin.koin.inject<ChannelSelector>()
//    val con: ConnectionAcceptor = SerialConnectionAcceptor("/dev/ttyACM0")
//    val con: ConnectionAcceptor = BluetoothServerConnectionAcceptor(BluetoothAdapter.getAdapters().first())
    while (coroutineContext.isActive) {
        logger.info { "Wait a client..." }
        val newClient = server.connection()
        logger.info { "Client connected!" }
        MultiplexerImpl(
            channel = newClient,
            idOdd = true,
            ioCoroutineScope = CoroutineScope(Dispatchers.IO)
        ).use { multiplexer ->
            while (coroutineContext.isActive) {
                val newClient = multiplexer.accept()
                CoroutineScope(Dispatchers.IO).launch {
                    newClient.use { client ->
                        channelSelector.processing(client)
                    }
//                    clientProcessing(newClient, selector)
                }
            }
        }
    }
}

//suspend fun clientProcessing(connection: DuplexChannel, selector: SelectorManager) {
//    ChannelSelector.processing(connection, selector)
//}
