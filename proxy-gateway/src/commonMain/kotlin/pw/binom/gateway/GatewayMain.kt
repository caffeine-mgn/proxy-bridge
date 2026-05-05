package pw.binom.gateway

import io.github.oshai.kotlinlogging.KotlinLogging
import io.klogging.logger
import io.ktor.network.selector.*
import kotlinx.coroutines.*
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose
import pw.binom.*
import pw.binom.channel.ChannelSelector
import pw.binom.channel.FileChannel
import pw.binom.channel.TcpConnectChannel
import pw.binom.com.comSerialKoinModule
import pw.binom.io.SelectorManagerKoinModule
import pw.binom.multiplexer.Multiplexer
import pw.binom.multiplexer.MultiplexerHolder
import pw.binom.multiplexer.MultiplexerImpl
import kotlin.coroutines.coroutineContext
import kotlin.use

private val logger = KotlinLogging.logger("GLOBAL")
suspend fun main(args: Array<String>) {
    logger.info { "STARTUP!" }
    val koin = startKoin {
        modules(
            comSerialKoinModule(
//                serialName = lazyOf("/dev/ttyACM0")
                serialName = lazyOf("COM4")
            ),
            SelectorManagerKoinModule,
            ChannelSelector.module,
            FileChannel.module,
            TcpConnectChannel.module,
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
        println("Client connected!")
        MultiplexerImpl(
            channel = newClient,
            idOdd = true,
            ioCoroutineScope = CoroutineScope(Dispatchers.IO)
        ).use { multiplexer ->
            while (coroutineContext.isActive) {
                val newClient = multiplexer.accept()
                CoroutineScope(Dispatchers.IO).launch {
                    newClient.use { client ->
                        channelSelector.processing(client, selector)
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
