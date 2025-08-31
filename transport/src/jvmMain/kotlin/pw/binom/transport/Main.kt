@file:JvmName("MainJvm")

package pw.binom.transport

import kotlinx.coroutines.runBlocking
import pw.binom.io.use
import pw.binom.transport.services.SpeedTestService
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

private const val addressAndChannel = "105FADED5516"
fun main(args: Array<String>) {
    val cmd = args.getOrNull(0) ?: "speedtest-upload"
    when (cmd) {
        "server" -> BluetoothServer.start()
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
