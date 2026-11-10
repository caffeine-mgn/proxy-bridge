package pw.binom.hid

/**
 * macOS: HID-вывод требует IOHIDUserDevice/IOHIDManager и прав на ввод —
 * отдельная задача. Пока не поддержано.
 */
actual fun openHidDevice(path: String): HidDevice =
    throw UnsupportedOperationException("HID output is not supported on macOS yet")

actual fun isHidSupported(): Boolean = false
