package pw.binom.bootstrap

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.logging.Logger
import javax.bluetooth.LocalDevice
import javax.bluetooth.RemoteDevice
import javax.bluetooth.ServiceRecord
import javax.microedition.io.Connector
import javax.microedition.io.StreamConnection
import kotlin.time.Duration.Companion.seconds

object BootstrapBluetoothClient {
    private val logger = Logger.getLogger(this::class.java.name)
    const val SPPServerName = "SPPServer"

    private class RemoteDeviceImpl(address: String) : RemoteDevice(address)

    fun start(file:File,addressAndChannel:String) {
        while (!Thread.currentThread().isInterrupted) {
            val workPc = RemoteDeviceImpl(addressAndChannel)
            val uuidSet = arrayOf(javax.bluetooth.UUID(0x1101)) // UUID для Serial Port Profile (SPP)
            val attrIDs = intArrayOf(0x0100) // Атрибуты сервиса
            val localDevice = LocalDevice.getLocalDevice()
            val services = runBlocking {
                withTimeoutOrNull(5.seconds) {
                    localDevice.discoveryAgent.asyncSearchServices(
                        attrSet = attrIDs,
                        uuidSet = uuidSet,
                        device = workPc,
                    )
                }
            }

            if (services == null) {
                logger.info("Can't get service for device $addressAndChannel")
                Thread.sleep(5.seconds.inWholeMilliseconds)
                continue
            }

            val sspUrl = services.find { it.type == SPPServerName }?.url
            if (sspUrl == null) {
                logger.info("Can't find SSP on device $addressAndChannel")
                Thread.sleep(5.seconds.inWholeMilliseconds)
                continue
            }
            val connection = Connector.open(sspUrl) as StreamConnection
            connection.openInputStream().use { inputStream ->
                connection.openOutputStream().use { outputStream ->
                    outputStream.write(Codes.START_FILE.toInt())
                    outputStream.writeString(file.name)
                    outputStream.writeInt(file.length().toInt())
                    file.inputStream().use { input ->
                        input.copyTo2(outputStream) { bytesCopied ->
                            val percent = (bytesCopied.toDouble()) / (file.length().toDouble()) * 100.0
                            println("Progress $percent%")
                        }
                    }
                }
            }
            connection.close()
            break
        }
    }
}

inline fun Int.eachByteIndexed(func: (Byte, Int) -> Unit) {
    func((this ushr (8 * (3 - 0))).toByte(), 0)
    func((this ushr (8 * (3 - 1))).toByte(), 1)
    func((this ushr (8 * (3 - 2))).toByte(), 2)
    func((this ushr (8 * (3 - 3))).toByte(), 3)
}

fun Int.toByteArray(): ByteArray {
    val output = ByteArray(Int.SIZE_BYTES)
    eachByteIndexed { value, index ->
        output[index] = value
    }
    return output
}


fun OutputStream.writeInt(value: Int) {
    write(value.toByteArray())
}

fun OutputStream.writeString(value: String) {
    writeInt(value.length)
    write(value.encodeToByteArray())
}

fun InputStream.readFully(dest: ByteArray, offset: Int = 0, length: Int = dest.size - offset) {
    var wasRead = 0
    while (true) {
        val r = read(dest, offset + wasRead, length - wasRead)
        if (r > 0) {
            wasRead += r
            if (wasRead == length) {
                return
            } else {
                continue
            }
        }
        if (wasRead > 0) {
            throw IllegalStateException("PackageBreak")
        } else {
            throw EOFException()
        }
    }
}

fun InputStream.copyTo2(dest: OutputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE, progress: (Long) -> Unit): Long {
    var bytesCopied = 0L;
    val buffer = ByteArray(bufferSize)

    while (true) {
        val bytes = read(buffer)
        if (bytes < 0) {
            break
        }
        bytesCopied += bytes
        dest.write(buffer, 0, bytes)
        progress(bytesCopied)
    }
    return bytesCopied
}

fun InputStream.readInt(): Int {
    val e = ByteArray(4)
    readFully(e)
    return Int.fromBytes(e[0], e[1], e[2], e[3])
}

fun InputStream.readString(): String {
    val size = readInt()
    val bytes = ByteArray(size)
    readFully(bytes)
    return bytes.decodeToString()
}

fun Int.Companion.fromBytes(byte0: Byte, byte1: Byte, byte2: Byte, byte3: Byte): Int =
    ((byte0.toInt() and 0xFF) shl 24) +
            ((byte1.toInt() and 0xFF) shl 16) +
            ((byte2.toInt() and 0xFF) shl 8) +
            ((byte3.toInt() and 0xFF) shl 0)


val ServiceRecord.type
    get() = getAttributeValue(256)?.value as String?

val ServiceRecord.url: String?
    get() = getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false)
