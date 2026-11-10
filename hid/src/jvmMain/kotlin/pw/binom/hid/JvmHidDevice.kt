package pw.binom.hid

import java.io.FileOutputStream

private class JvmHidDevice(private val path: String) : HidDevice {
    private val stream = FileOutputStream(path)

    override val reportLength: Int = 8

    override fun writeReport(report: ByteArray) {
        report.requireReportLength(reportLength, path)
        stream.write(report)
        // Каждый отчёт — сразу в устройство: задержка ядра или буферизация
        // ломают тайминги имитации ввода.
        stream.flush()
    }

    override fun close() {
        stream.close()
    }
}

actual fun openHidDevice(path: String): HidDevice = JvmHidDevice(path)

actual fun isHidSupported(): Boolean = true
