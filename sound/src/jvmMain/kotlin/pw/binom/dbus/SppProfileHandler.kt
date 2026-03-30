package pw.binom.dbus

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import org.freedesktop.dbus.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.concurrent.thread


// UUID для SPP (Serial Port Profile)
const val SPP_UUID = "00001101-0000-1000-8000-00805f9b34fb"
//const val SPP_UUID = "00001101-0000-2000-8000-00805f9b34fb"
const val PROFILE_OBJECT_PATH = "/com/example/spp/profile"
const val BUS_NAME = "com.example.spp.service"

class SppProfileHandler : BlueZProfile {

    override fun Release() {
        println("🔌 Profile released by BlueZ")
    }

    override fun NewConnection(
        device: DBusPath,
        fd: FileDescriptor,  // ← dbus-java FileDescriptor
        fdProperties: Map<String, Variant<*>>
    ) {
        println("📡 New connection from: ${device.path}")
        println("   FD properties: $fdProperties")

        // Конвертируем в java.io.FileDescriptor для работы с потоками
        // ISocketProvider = null → используем reflection fallback
        val javaFd = fd.toJavaFileDescriptor(null)

        thread(name = "spp-handler-${device.path}") {
            var inputStream: FileInputStream? = null
            var outputStream: FileOutputStream? = null
            try {
                inputStream = FileInputStream(javaFd)
                outputStream = FileOutputStream(javaFd)

                // Пример: эхо-сервер
                val buffer = ByteArray(1024)
                while (!Thread.currentThread().isInterrupted) {
                    val read = inputStream.read(buffer)
                    if (read <= 0) break
                    outputStream.write(buffer, 0, read)
                    outputStream.flush()
                }
            } catch (e: Exception) {
                println("❌ Connection error: ${e.message}")
            } finally {
                // Закрываем только потоки, НЕ сам javaFd (управляется BlueZ)
                try { inputStream?.close() } catch (_: Exception) {}
                try { outputStream?.close() } catch (_: Exception) {}
            }
        }
    }

    override fun RequestDisconnection(device: DBusPath) {
        println("🔌 Disconnection requested for: ${device.path}")
    }

    override fun getObjectPath(): String = PROFILE_OBJECT_PATH
    override fun isRemote(): Boolean = false
}
