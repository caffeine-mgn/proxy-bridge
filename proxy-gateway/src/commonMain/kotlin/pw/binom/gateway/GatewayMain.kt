package pw.binom.gateway

import io.ktor.network.selector.*
import kotlinx.coroutines.*
import pw.binom.channel.TcpConnectChannel
import pw.binom.multiplexer.DuplexChannel
import pw.binom.multiplexer.Multiplexer
import kotlin.io.println
import kotlin.use

suspend fun main(args: Array<String>) {
    withContext(Dispatchers.IO) {
        SelectorManager(Dispatchers.IO).use { selector ->
            val adapter = pw.binom.bluetooth.BluetoothAdapter.getAdapters().first()
            adapter.listenSPP().use { server ->
                while (coroutineContext.isActive) {
                    println("Wait a client")
                    val newClient = server.accept()
                    println("Client connected!")
                    launch {
                        Multiplexer(
                            channel = newClient,
                            idOdd = true,
                            ioCoroutineScope = CoroutineScope(Dispatchers.IO)
                        ).use { multiplexer ->
                            while (isActive) {
                                val newClient = multiplexer.accept()
                                CoroutineScope(Dispatchers.IO).launch {
                                    clientProcessing(newClient, selector)
                                }
                            }
                        }
                    }
                    println("Client connected")
                }
            }
        }
    }
}

suspend fun clientProcessing(connection: DuplexChannel, selector: SelectorManager) {
    val buff = connection.receive()
    println("Income ${buff.size} bytes")
    val cmd = buff.readByte()
    println("Command: $cmd")
    TcpConnectChannel.income(selector = selector, channel = connection, buffer = buff)
}
