@file:JvmName("MainJvm")

package pw.binom

import dev.bluefalcon.core.BlueFalcon
import dev.bluefalcon.engine.rpi.RpiEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import pw.binom.bluetooth.asyncSearchServices
import pw.binom.bluetooth.type
import pw.binom.bluetooth.url
import javax.bluetooth.LocalDevice
import javax.microedition.io.Connector
import javax.microedition.io.StreamConnection
import javax.microedition.io.StreamConnectionNotifier
import kotlin.time.Duration.Companion.seconds

fun main() {
    runBlocking {
        val blueFalcon = BlueFalcon {
            this.engine = RpiEngine()
        }
        blueFalcon.scan()
        blueFalcon.peripherals.collect { list ->
            list.forEach {
                println("-->${it}")
            }
        }
    }
//    println("blueFalcon.isScanning=${blueFalcon.isScanning}")
}

const val workPcAddress = "0C9A3CEA4C09"
const val SPPServerName = "SPPServer"
fun main2() {
    runBlocking {
        val localDevice = LocalDevice.getLocalDevice()
//        val devices = localDevice.discoveryAgent.asyncDiscover()
//        devices.forEach {
//            println("->${it.getFriendlyName(false)} ${it.bluetoothAddress}")
//        }
//        val workPc =
//            devices.find { it.bluetoothAddress == workPcAddress } ?: throw IllegalStateException("Work PC not found")

        val workPc = RemoteDeviceImpl(workPcAddress)
        // Поиск сервисов
        val uuidSet = arrayOf(javax.bluetooth.UUID(0x1101)) // UUID для Serial Port Profile (SPP)
        val attrIDs = intArrayOf(0x0100) // Атрибуты сервиса

        val services = withTimeout(5.seconds) {
            localDevice.discoveryAgent.asyncSearchServices(
                attrSet = attrIDs,
                uuidSet = uuidSet,
                device = workPc,
            )
        }
        val sspUrl = services.find { it.type == SPPServerName }?.url
        println("->$sspUrl")
//        val url = services.first().getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
    }
}

fun main1() {
    try {
        // Инициализация локального Bluetooth устройства
        val localDevice = LocalDevice.getLocalDevice()
        println("Адрес Bluetooth устройства: ${localDevice.bluetoothAddress}")
        println("Имя Bluetooth устройства: ${localDevice.friendlyName}")
//val uuid = UUID("1101", true)
        val uuid = "0000110100002000800000805f9b34fb"
        // Публикация сервиса SPP
        val url = "btspp://localhost:$uuid;name=SPPServer"
        println("URL: $url")
        val notifier = Connector.open(url) as StreamConnectionNotifier
        println("Сервис SPP опубликован: $url")

        // Ожидание подключения клиента
        println("Ожидание подключения клиента...")
        val connection = notifier.acceptAndOpen() as StreamConnection
        println("Клиент подключен!")

        // Обмен данными
        val outputStream = connection.openOutputStream()
        val inputStream = connection.openInputStream()

        // Отправка данных клиенту
        val message = "Hello from server!"
        outputStream.write(message.toByteArray())
        println("Отправлено клиенту: $message")

        // Чтение данных от клиента
        val buffer = ByteArray(1024)
        val bytesRead = inputStream.read(buffer)
        val response = String(buffer, 0, bytesRead)
        println("Получено от клиента: $response")

        // Закрытие соединения
        outputStream.close()
        inputStream.close()
        connection.close()
        notifier.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
