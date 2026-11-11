package pw.binom.usb.gadget

/** macOS не умеет быть USB-устройством — только хостом. */
actual fun isUsbGadgetSupported(): Boolean = false

actual fun createSymbolicLink(link: String, target: String) {
    throw UnsupportedOperationException("USB gadget (configfs) is not supported on macOS")
}
