@file:JvmName("MainJvm")
package pw.binom

import com.fazecast.jSerialComm.SerialPort
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.file
import io.ktor.utils.io.streams.writePacket
import pw.binom.io.buildBuffer

object Upload : CliktCommand() {
    private val port by option("--port", "-p").required()
    private val file by option("--file", "-f").file(mustExist = true, mustBeReadable = true)
        .required()

    override fun run() {
        val port = SerialPort.getCommPort(port)
        check(port.openPort()) { "Can't open $port" }
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0)
        try {
            port.outputStream.writePacket(buildBuffer {
                this.writeInt(file.length().toInt())
            })
            file.inputStream().use { input ->
                input.copyTo(port.outputStream)
            }
        } finally {
            port.closePort()
        }
    }

}

fun main(args: Array<String>) {
    Upload.main(args)
}
