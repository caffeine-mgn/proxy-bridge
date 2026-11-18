package pw.binom.usb.gadget

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.use

/**
 * Есть ли на этой платформе configfs-гаджетов USB (Linux).
 * На Windows/macOS устройство не может притворяться и COM-портом, и клавиатурой,
 * поэтому там всё это — no-op с объяснением.
 */
expect fun isUsbGadgetSupported(): Boolean

/** Создать символическую ссылку [link] → [target] (target может быть относительным). */
expect fun createSymbolicLink(link: String, target: String)

/**
 * Загрузить модули ядра, без которых configfs-гаджетов не существует
 * (`libcomposite`, `usb_f_acm`, `usb_f_hid`). Нужен root.
 */
expect fun loadUsbGadgetModules(): Boolean

/**
 * Состояние USB-гаджета: прикидывается ли устройство COM-портом и клавиатурой.
 */
data class GadgetStatus(
    /** configfs-гаджеты доступны в принципе. */
    val supported: Boolean,
    /** Имя UDC-контроллера (например, fe980000.usb), если найден. */
    val udc: String? = null,
    /** Состояние UDC: `configured`, `not attached`, `not found`. */
    val udcState: String? = null,
    /** Функции, включённые в конфигурацию гаджета. */
    val functions: List<String> = emptyList(),
    /** Путь к последовательному порту на нашей стороне. */
    val serialDevice: String? = null,
    /** Путь к HID-устройству (клавиатуре) на нашей стороне. */
    val keyboardDevice: String? = null,
    /** Что не так — человеческим языком. */
    val problems: List<String> = emptyList(),
) {
    /** Всё на месте: устройство видно хосту как COM + клавиатура. */
    val ready: Boolean
        get() = supported && serialDevice != null && keyboardDevice != null &&
            functions.contains(UsbGadgetManager.ACM_FUNCTION) &&
            functions.contains(UsbGadgetManager.HID_FUNCTION)
}

/**
 * Настройка USB-гаджета через configfs: устройство с малины выглядит на хосте
 * одновременно как COM-порт (CDC-ACM) и как клавиатура (HID boot protocol).
 *
 * Запись в configfs требует root — этим занимается systemd-юнит или запуск
 * приложения от root. Если прав нет, [ensureConfigured] вернёт статус с
 * объяснением, а не упадёт.
 */
class UsbGadgetManager(
    /** Имя гаджета в configfs. */
    val gadgetName: String = "g1",
    /** UDC; null — взять первый доступный из /sys/class/udc. */
    val udcOverride: String? = null,
    /** Имена узлов на нашей стороне (создаются ядром). */
    val serialDevicePath: String = "/dev/ttyGS0",
    val keyboardDevicePath: String = "/dev/hidg0",
) {
    companion object {
        const val CONFIGFS_ROOT = "/sys/kernel/config/usb_gadget"
        const val UDC_ROOT = "/sys/class/udc"
        const val ACM_FUNCTION = "acm.usb0"
        const val HID_FUNCTION = "hid.usb0"

        const val ID_VENDOR = "0x1d6b"
        const val ID_PRODUCT = "0x0104"
        const val SERIAL_NUMBER = "1234567890"
        const val MANUFACTURER = "Raspberry Pi"
        const val PRODUCT = "USB Proxy Bridge (COM + Keyboard)"
        const val CONFIG_NAME = "Config 1: CDC ACM + HID"
        const val MAX_POWER = "250"

        /** Boot-клавиатура: 8-байтовые отчёты, стандартный дескриптор. */
        private val REPORT_DESCRIPTOR_BYTES = intArrayOf(
            0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x05, 0x07,
            0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01,
            0x75, 0x01, 0x95, 0x08, 0x81, 0x02, 0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
            0x95, 0x05, 0x75, 0x01, 0x05, 0x08, 0x19, 0x01, 0x29, 0x05,
            0x91, 0x02, 0x95, 0x01, 0x75, 0x03, 0x91, 0x01, 0x95, 0x06,
            0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07, 0x19, 0x00,
            0x29, 0x65, 0x81, 0x00, 0xC0,
        )

        /** Boot-клавиатура: 8-байтовые отчёты, стандартный дескриптор. */
        val REPORT_DESCRIPTOR: ByteArray = ByteArray(REPORT_DESCRIPTOR_BYTES.size) {
            REPORT_DESCRIPTOR_BYTES[it].toByte()
        }
    }

    private val gadgetPath = Path(CONFIGFS_ROOT, gadgetName)

    private fun readTextOrNull(path: Path): String? = runCatching {
        if (!SystemFileSystem.exists(path)) return null
        SystemFileSystem.source(path).buffered().use { it.readString() }.trim()
    }.getOrNull()

    private fun writeText(path: Path, value: String) {
        SystemFileSystem.sink(path).buffered().use { it.writeString(value) }
    }

    private fun findUdc(): String? {
        val root = Path(UDC_ROOT)
        if (!SystemFileSystem.exists(root)) return null
        return SystemFileSystem.list(root).firstOrNull()?.name
    }

    /** Текущее состояние: что включено, что доступно, чего не хватает. */
    fun status(): GadgetStatus {
        if (!isUsbGadgetSupported()) {
            return GadgetStatus(
                supported = false,
                problems = listOf("USB-гаджет (configfs) доступен только на Linux"),
            )
        }
        val udc = udcOverride ?: findUdc()
        val problems = mutableListOf<String>()
        if (udc == null) {
            problems += "Не найден UDC: /sys/class/udc пуст. Включён ли dwc2 в режиме peripheral " +
                "(dtoverlay=dwc2,dr_mode=peripheral в config.txt и dwc2.dr_mode=peripheral в cmdline.txt)?"
        }
        if (!SystemFileSystem.exists(Path(CONFIGFS_ROOT))) {
            problems += "нет $CONFIGFS_ROOT: не загружен модуль libcomposite " +
                "(modprobe libcomposite usb_f_acm usb_f_hid)"
        }

        val functions = if (SystemFileSystem.exists(Path(gadgetPath, "functions"))) {
            SystemFileSystem.list(Path(gadgetPath, "functions")).map { it.name }
        } else {
            emptyList()
        }

        val udcState = findUdc()?.let { name ->
            readTextOrNull(Path(UDC_ROOT, name, "state")) ?: "unknown"
        }

        val serialDevice = serialDevicePath.takeIf { SystemFileSystem.exists(Path(it)) }
        val keyboardDevice = keyboardDevicePath.takeIf { SystemFileSystem.exists(Path(it)) }

        if (functions.isEmpty()) problems += "Гаджет $gadgetName не создан"
        if (functions.isNotEmpty() && !functions.contains(ACM_FUNCTION)) {
            problems += "В гаджете нет функции $ACM_FUNCTION (COM-порт)"
        }
        if (functions.isNotEmpty() && !functions.contains(HID_FUNCTION)) {
            problems += "В гаджете нет функции $HID_FUNCTION (клавиатура)"
        }
        if (serialDevice == null) problems += "Нет $serialDevicePath — COM-порт на нашей стороне не создан"
        if (keyboardDevice == null) problems += "Нет $keyboardDevicePath — клавиатура не создана"

        return GadgetStatus(
            supported = true,
            udc = udc,
            udcState = udcState,
            functions = functions,
            serialDevice = serialDevice,
            keyboardDevice = keyboardDevice,
            problems = problems,
        )
    }

    /**
     * Приводит гаджет к нужному виду: создаёт (или пересоздаёт) конфигурацию
     * с COM-портом и клавиатурой и привязывает её к UDC.
     */
    fun ensureConfigured(): GadgetStatus {
        if (!isUsbGadgetSupported()) return status()

        // На чистой системе configfs-гаджетов нет, пока не загружен libcomposite.
        if (!SystemFileSystem.exists(Path(CONFIGFS_ROOT))) {
            println("USB gadget: загружаю модули ядра (libcomposite/usb_f_acm/usb_f_hid)")
            loadUsbGadgetModules()
        }

        val udc = udcOverride ?: findUdc()
            ?: return status()

        // Уже настроен и оба узла на месте — ничего не трогаем: пересоздание
        // гаджета переподключает устройство и меняет имя COM-порта на хосте.
        val current = status()
        if (current.ready && current.udcState == "configured") {
            return current
        }

        removeGadget()

        runCatching {
            SystemFileSystem.createDirectories(gadgetPath)
            writeText(Path(gadgetPath, "idVendor"), ID_VENDOR)
            writeText(Path(gadgetPath, "idProduct"), ID_PRODUCT)

            val stringsDir = Path(gadgetPath, "strings/0x409")
            SystemFileSystem.createDirectories(stringsDir)
            writeText(Path(stringsDir, "serialnumber"), SERIAL_NUMBER)
            writeText(Path(stringsDir, "manufacturer"), MANUFACTURER)
            writeText(Path(stringsDir, "product"), PRODUCT)

            val configDir = Path(gadgetPath, "configs/c.1")
            SystemFileSystem.createDirectories(configDir)
            SystemFileSystem.createDirectories(Path(configDir, "strings/0x409"))
            writeText(Path(configDir, "strings/0x409/configuration"), CONFIG_NAME)
            writeText(Path(configDir, "MaxPower"), MAX_POWER)

            SystemFileSystem.createDirectories(Path(gadgetPath, "functions", ACM_FUNCTION))

            val hidDir = Path(gadgetPath, "functions", HID_FUNCTION)
            SystemFileSystem.createDirectories(hidDir)
            writeText(Path(hidDir, "protocol"), "1")      // keyboard
            writeText(Path(hidDir, "subclass"), "1")      // boot interface
            writeText(Path(hidDir, "report_length"), "8")
            writeReportDescriptor(Path(hidDir, "report_desc"))

            // configfs резолвит цель ссылки от текущего каталога процесса, а не от
            // каталога ссылки, поэтому путь должен быть абсолютным.
            createSymbolicLink(
                link = Path(configDir, ACM_FUNCTION).toString(),
                target = Path(gadgetPath, "functions", ACM_FUNCTION).toString(),
            )
            createSymbolicLink(
                link = Path(configDir, HID_FUNCTION).toString(),
                target = Path(gadgetPath, "functions", HID_FUNCTION).toString(),
            )

            // Привязка к UDC — последней: до неё устройство не видно хосту.
            // UDC мог остаться занятым другим гаджетом — отвязываем его.
            unbindOtherGadgets(udc)
            writeText(Path(gadgetPath, "UDC"), udc)
        }.onFailure { error ->
            println("USB gadget setup failed: ${error::class.simpleName}: ${error.message}")
        }

        // Ядру нужно время на создание /dev/ttyGS* и /dev/hidg* и на само
        // перечисление устройства хостом (state=configured).
        var last = status()
        repeat(30) {
            last = status()
            val devicesReady = last.serialDevice != null && last.keyboardDevice != null
            if (devicesReady && last.udcState == "configured") return last
            if (devicesReady && it == 29) return last
            runBlocking { delay(100) }
        }
        return last
    }

    /** Отвязывает от [udc] все чужие гаджеты: иначе запись в UDC даёт EBUSY. */
    private fun unbindOtherGadgets(udc: String) {
        val root = Path(CONFIGFS_ROOT)
        if (!SystemFileSystem.exists(root)) return
        SystemFileSystem.list(root).forEach { gadget ->
            if (gadget.name == gadgetName) return@forEach
            val udcFile = Path(gadget, "UDC")
            val current = readTextOrNull(udcFile) ?: return@forEach
            if (current == udc) {
                println("USB gadget: отвязываю чужой гаджет ${gadget.name} от $udc")
                runCatching { writeText(udcFile, "") }
            }
        }
    }

    private fun writeReportDescriptor(path: Path) {
        SystemFileSystem.sink(path).buffered().use { sink ->
            sink.write(REPORT_DESCRIPTOR)
        }
    }

    /** Сносит гаджет целиком (сначала отвязка от UDC, потом удаление узлов). */
    private fun removeGadget() {
        if (!SystemFileSystem.exists(gadgetPath)) return
        runCatching { writeText(Path(gadgetPath, "UDC"), "") }
        runCatching {
            SystemFileSystem.list(Path(gadgetPath, "configs")).forEach { config ->
                runCatching {
                    SystemFileSystem.list(config).forEach { entry ->
                        if (entry.name.contains('.')) SystemFileSystem.delete(entry)
                    }
                }
            }
        }
        // Порядок удаления важен: configfs не даёт удалить непустой каталог.
        listOf(
            "configs/c.1/strings/0x409",
            "configs/c.1/strings",
            "configs/c.1",
            "functions/$ACM_FUNCTION",
            "functions/$HID_FUNCTION",
            "functions",
            "strings/0x409",
            "strings",
        ).forEach { relative ->
            runCatching { SystemFileSystem.delete(Path(gadgetPath, relative), mustExist = false) }
        }
        runCatching { SystemFileSystem.delete(gadgetPath, mustExist = false) }
    }
}
