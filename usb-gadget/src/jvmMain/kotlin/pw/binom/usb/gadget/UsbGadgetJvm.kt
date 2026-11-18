package pw.binom.usb.gadget

import java.nio.file.Files
import java.nio.file.Paths

actual fun isUsbGadgetSupported(): Boolean =
    System.getProperty("os.name", "").lowercase().contains("linux")

actual fun createSymbolicLink(link: String, target: String) {
    Files.createSymbolicLink(Paths.get(link), Paths.get(target))
}

actual fun loadUsbGadgetModules(): Boolean {
    if (!isUsbGadgetSupported()) return false
    // Каждый модуль отдельно: часть может быть встроена в ядро, и общий
    // вызов modprobe тогда завершится ошибкой.
    var loaded = false
    for (module in listOf("libcomposite", "usb_f_acm", "usb_f_hid")) {
        val result = runCatching {
            ProcessBuilder("modprobe", module)
                .redirectErrorStream(true)
                .start()
                .also { it.inputStream.readBytes() }
                .waitFor()
        }.getOrNull()
        if (result == 0) loaded = true
    }
    return loaded
}
