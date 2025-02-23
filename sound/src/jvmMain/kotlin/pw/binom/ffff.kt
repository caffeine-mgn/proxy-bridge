@file:JvmName("MainJvm")

package pw.binom

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

/*
import javax.sound.sampled.*

private fun printDevices() {
    val devices = AudioSystem.getMixerInfo()
    devices.forEachIndexed { index, mixerInfo ->
        println("[$index] ${mixerInfo.name}: ${mixerInfo.description}")
        val mixer = AudioSystem.getMixer(mixerInfo)
        (mixer.targetLineInfo.asSequence() + mixer.sourceLineInfo.asSequence()).forEach { lineInfo ->

            if (lineInfo is DataLine.Info) {
                println("  ${lineInfo.lineClass.simpleName}: ${lineInfo.formats.size}")
                lineInfo.formats.forEach { format ->
                    println("    $format")
                }
            } else {
                println("  ${lineInfo.lineClass.simpleName}:")
            }
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
fun readAny(deviceIndex: Int) {
    val devices = AudioSystem.getMixerInfo()
    val mixer = AudioSystem.getMixer(devices[deviceIndex])
    val format = AudioFormat(96000f, 32, 2, true, true)
    val line = mixer.getLine(DataLine.Info(TargetDataLine::class.java, format)) as TargetDataLine
    line.open(format)
    line.start()
    val buf = ByteArray(8)
    while (true) {
        val len = line.read(buf, 0, buf.size)
        val anyData = buf.any { it != 0.toByte() }
        if (anyData) {
            val data = Long.fromBytes(buf)
            println("---> $data ${data.toULong().toString(2)} ${buf.toHexString()}")
        }
    }
    line.close()
}

fun send(deviceIndex: Int, data: Long) {
    val devices = AudioSystem.getMixerInfo()
    val mixer = AudioSystem.getMixer(devices[deviceIndex])
    val format = AudioFormat(96000f, 32, 2, true, true)
    val line = mixer.getLine(DataLine.Info(SourceDataLine::class.java, format)) as SourceDataLine
    line.open(format)
    line.start()
    val d = data.toByteArray()
    val wrote = line.write(d, 0, d.size)
    println("Wroted $wrote")
    line.drain()
    line.close()
}

@OptIn(ExperimentalStdlibApi::class)
fun noise(deviceIndex: Int, level: Float) {
    val sampleRate = 96000
    val devices = AudioSystem.getMixerInfo()
    val mixer = AudioSystem.getMixer(devices[deviceIndex])
    val format = AudioFormat(sampleRate.toFloat(), 16, 2, true, true)
    val line = mixer.getLine(DataLine.Info(SourceDataLine::class.java, format)) as SourceDataLine
    line.open(format)
    line.start()

    var baseFrequency = 440.0 // Начальная частота (A4)
    val amplitude = 0.5 // Амплитуда (громкость)
    var time = 0.0

    val data = ByteArray(16){1}
//    levelShort.toByteArray(data, 0)
//    levelShort.toByteArray(data, 8)
    println("--->${data.toHexString()}")
    println("line.format=${line.format}")
    while (true) {
        // Изменение частоты со временем
        val frequency =baseFrequency + 220.0 * kotlin.math.sin(2 * kotlin.math.PI * 0.5 * time)

        // Генерация синусоидального тона
        val sample = amplitude * kotlin.math.sin(2 * kotlin.math.PI * frequency * time)

        // Преобразование в 16-битный звук
        val sampleShort = (sample * Short.MAX_VALUE).toInt().toShort()

        // Запись в левый и правый каналы
        sampleShort.toByteArray(data,0)
        sampleShort.toByteArray(data,Short.SIZE_BYTES)

        // Увеличение времени
        time += 1.0 / sampleRate

        val wrote = line.write(data, 0, data.size)
        line.drain()
    }
    line.close()
}

@OptIn(ExperimentalStdlibApi::class)
fun main(args: Array<String>) {
    when (args[0]) {
        "devices" -> printDevices()
        "read" -> readAny(args[1].toInt())
        "write" -> send(args[1].toInt(), args[2].toULong(2).toLong())
        "noise" -> noise(args[1].toInt(), args[2].toFloat())
    }
}
*/
const val workPcAddress = "0C9A3CEA4C09"
const val SPPServerName = "SPPServer"
fun main() {
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
