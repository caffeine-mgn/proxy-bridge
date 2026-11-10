package pw.binom.hid

/**
 * Windows: HID-вывод в чужую систему требует отдельного драйвера/API
 * (Windows не даёт писать в hidraw-подобный узел). Пока не поддержано —
 * проброс с этой стороны делается через последовательный порт.
 */
actual fun openHidDevice(path: String): HidDevice =
    throw UnsupportedOperationException("HID output is not supported on Windows yet")

actual fun isHidSupported(): Boolean = false
