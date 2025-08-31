package pw.binom.transport

import javax.bluetooth.LocalDevice
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.util.logging.Logger
import javax.microedition.io.Connector
import javax.microedition.io.StreamConnection
import javax.microedition.io.StreamConnectionNotifier

object BootstrapBluetoothServer {
    private val logger = Logger.getLogger(this::class.java.name)
    fun start() {
        val localDevice = LocalDevice.getLocalDevice()
        logger.info("Адрес Bluetooth устройства: ${localDevice.bluetoothAddress}")
        logger.info("Имя Bluetooth устройства: ${localDevice.friendlyName}")
        val uuid = "0000110100002000800000805f9b34fb"
        val url = "btspp://localhost:$uuid;name=SPPServer"
        val streamConnectionNotifier = Connector.open(url) as StreamConnectionNotifier
        try {
            while (!Thread.currentThread().isInterrupted) {
                logger.info("Waiting a client...")
                val input = streamConnectionNotifier.acceptAndOpen()
                logger.info("Client connected")
                val thread = ClientThread(input)
                thread.start()
            }
        } finally {
            streamConnectionNotifier.close()
        }
    }

    class ClientThread(val connection: StreamConnection) : Thread() {
        private val input = connection.openInputStream()
        private val output = connection.openOutputStream()
        override fun run() {
            try {
                while (!isInterrupted) {
                    val data = input.read()
                    if (data < 0) {
                        break
                    }
                    val code = data.toUByte().toByte()
                    when (code) {
                        Codes.START_FILE -> try {
                            startFile(input)
                        } catch (e: Throwable) {
                            throw IllegalStateException("Can't processing input file", e)
                        }

                        else -> TODO("Unknown code = $code")
                    }
                }
            } finally {
                connection.close()
            }
        }
    }

    fun startFile(stream: InputStream) {
        logger.info("Start file processing")
        val name = stream.readString()
        logger.info("File name: $name")
        var size = stream.readInt()
        val buffer = ByteArray(1024 * 1024)
        File(name).outputStream().use { out ->
            while (size > 0) {
                val l = stream.read(buffer, 0, minOf(buffer.size, size))
                if (l <= 0) {
                    throw EOFException()
                }
                out.write(buffer, 0, l)
            }
        }
        logger.info("File $name successful saved")
    }
}
