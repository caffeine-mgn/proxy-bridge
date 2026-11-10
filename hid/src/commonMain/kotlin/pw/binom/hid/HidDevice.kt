package pw.binom.hid

/**
 * Устройство вывода HID-отчётов: на Raspberry Pi (USB gadget) это
 * `/dev/hidg0`, на хосте с реальной клавиатурой — соответствующий hidraw-узел.
 *
 * Пишет ровно один отчёт целиком: буферизация и «склейка» двух отчётов в один
 * write недопустимы — ядро разбирает запись как один отчёт фиксированной длины.
 */
interface HidDevice {
    /** Размер отчёта (для boot-клавиатуры — 8 байт). */
    val reportLength: Int

    fun writeReport(report: ByteArray)

    fun close()
}

/** Открывает HID-устройство вывода по пути (`/dev/hidg0`). */
expect fun openHidDevice(path: String): HidDevice

expect fun isHidSupported(): Boolean

internal fun ByteArray.requireReportLength(reportLength: Int, path: String) {
    require(size == reportLength) {
        "HID report for $path must be exactly $reportLength bytes, got $size"
    }
}
