package pw.binom.usb.gadget

import java.nio.file.Files
import java.nio.file.Paths

actual fun isUsbGadgetSupported(): Boolean =
    System.getProperty("os.name", "").lowercase().contains("linux")

actual fun createSymbolicLink(link: String, target: String) {
    Files.createSymbolicLink(Paths.get(link), Paths.get(target))
}
