package pw.binom.usb.gadget

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.errno
import platform.posix.strerror
import platform.posix.system
import platform.posix.symlink

actual fun isUsbGadgetSupported(): Boolean = true

@OptIn(ExperimentalForeignApi::class)
actual fun createSymbolicLink(link: String, target: String) {
    val result = symlink(target, link)
    if (result != 0) {
        throw IllegalStateException(
            "symlink($link -> $target) failed: ${strerror(errno)?.toKString()}",
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun loadUsbGadgetModules(): Boolean {
    // system(3) возвращает статус оболочки; 0 — успех.
    for (module in listOf("libcomposite", "usb_f_acm", "usb_f_hid")) {
        system("modprobe $module")
    }
    return true
}
