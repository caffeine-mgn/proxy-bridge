@file:JvmName("MainJvm")
package pw.binom

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import io.ktor.utils.io.streams.writePacket
import pw.binom.io.buildBuffer
import pw.binom.serial.SerialConfig
import pw.binom.serial.openSerialPort
import java.io.ByteArrayOutputStream

object Upload : CliktCommand() {
    private val portName by option("--port", "-p").required()
    private val file by option("--file", "-f").file(mustExist = true, mustBeReadable = true)
        .required()

    override fun run() {
        val header = ByteArrayOutputStream().use { out ->
            out.writePacket(buildBuffer { writeInt(file.length().toInt()) })
            out.toByteArray()
        }
        openSerialPort(
            portName,
            SerialConfig(
                baudRate = 115200,
                writeTimeoutMs = 30_000,
            ),
        ).use { port ->
            port.write(header)
            file.inputStream().use { input ->
                val chunk = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(chunk)
                    if (read <= 0) break
                    port.write(chunk, 0, read)
                }
            }
        }
    }
}

fun main(args: Array<String>) {
    Upload.main(args)
}
