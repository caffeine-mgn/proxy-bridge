@file:JvmName("MainJvm")

package pw.binom.transport

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import pw.binom.ByteBufferPool
import pw.binom.Environment
import pw.binom.availableProcessors
import pw.binom.io.httpServer.HttpServer2
import pw.binom.io.socket.InetSocketAddress
import pw.binom.io.use
import pw.binom.io.useAsync
import pw.binom.network.MultiFixedSizeThreadNetworkDispatcher
import pw.binom.transport.controllers.ProxyHandler
import pw.binom.transport.services.SpeedTestService
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

private const val addressAndChannel = "105FADED5516"

suspend fun client(manager: VirtualManager) {
    MultiFixedSizeThreadNetworkDispatcher(Environment.availableProcessors).use { networkManager ->
        val s = HttpServer2(
            handler = ProxyHandler(manager = manager),
            dispatcher = networkManager,
            byteBufferPool = ByteBufferPool(size = DEFAULT_BUFFER_SIZE, initCapacity = 16)
        )
        println("Start listening on 8050")
        s.listen(InetSocketAddress.resolve(host = "127.0.0.1", port = 8050))
        while (coroutineContext.isActive) {
            delay(10.seconds)
        }
    }
}

fun main(args: Array<String>) {
    val cmd = args.getOrNull(0) ?: "client"
    when (cmd) {
        "server" -> BluetoothServer.start()
        "client" -> {
            BluetoothClient.start(
                remoteAddress = addressAndChannel,
            ).use { manager ->
                runBlocking {
                    client(manager)
                }
            }
        }

        "speedtest-download" -> {
            BluetoothClient.start(
                remoteAddress = addressAndChannel,
            ).use { manager ->
                val (bytes, time) = measureTimedValue {
                    runBlocking {
                        SpeedTestService.download(manager = manager, 10.seconds)
                    }
                }
                println("Download $bytes in $time: ${bytes.toDouble() / time.inWholeMilliseconds.toDouble() / 1000.0} bytes/sec")
            }
        }

        "speedtest-upload" -> {
            BluetoothClient.start(
                remoteAddress = addressAndChannel,
            ).use { manager ->
                val (bytes, time) = measureTimedValue {
                    runBlocking {
                        SpeedTestService.upload(manager = manager, 10.seconds)
                    }
                }
                println("Download $bytes in $time: ${bytes.toDouble() / time.inWholeMilliseconds.toDouble() / 1000.0} bytes/sec")
            }
        }
    }

//    BootstrapBluetoothClient.start()
}
