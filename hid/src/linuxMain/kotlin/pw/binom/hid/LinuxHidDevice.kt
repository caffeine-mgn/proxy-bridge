package pw.binom.hid

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.errno
import platform.posix.open
import platform.posix.strerror
import platform.posix.write

/**
 * Linux: пишем прямо в узел HID-функции USB-гаджета (`/dev/hidg0`).
 *
 * Один отчёт = один `write(2)`: ядро разбирает запись как целый отчёт, поэтому
 * длина должна быть ровно `report_length`, а частичная запись считается
 * ошибкой — иначе на целевой машине окажется «половина» нажатия.
 */
@OptIn(ExperimentalForeignApi::class)
private class LinuxHidDevice(private val path: String) : HidDevice {
    private val fd = open(path, O_WRONLY)

    override val reportLength: Int = 8

    init {
        if (fd < 0) {
            throw HidOpenException(path, errno, strerror(errno)?.toString() ?: "open failed")
        }
    }

    override fun writeReport(report: ByteArray) {
        report.requireReportLength(reportLength, path)
        val written = report.usePinned { pinned ->
            write(fd, pinned.addressOf(0), report.size.toULong())
        }
        if (written.toInt() != report.size) {
            throw HidWriteException(
                path,
                "expected ${report.size} bytes, written $written (errno=$errno: ${strerror(errno)})",
            )
        }
    }

    override fun close() {
        close(fd)
    }
}

actual fun openHidDevice(path: String): HidDevice = LinuxHidDevice(path)

actual fun isHidSupported(): Boolean = true

class HidOpenException(val path: String, val errno: Int, message: String) :
    RuntimeException("Cannot open HID device $path: $message (errno=$errno)")

class HidWriteException(val path: String, message: String) :
    RuntimeException("Cannot write HID report to $path: $message")
