package pw.binom.usb.gadget

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.errno
import platform.posix.strerror
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
