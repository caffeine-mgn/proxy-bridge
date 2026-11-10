package pw.binom.proxy

import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import pw.binom.hid.Keyboard
import pw.binom.hid.TypingProfile
import pw.binom.hid.openHidDevice
import kotlin.use

/**
 * Консольные команды, не требующие конфигурации и сети.
 *
 * Нужны для имитации ввода с клавиатуры: одна и та же команда работает и на
 * Raspberry Pi (пишет в `/dev/hidg0` USB-гаджета), и на обычном Linux.
 */
object Cli {
    const val DEFAULT_HID_DEVICE = "/dev/hidg0"

    /** Возвращает true, если аргументы — известная CLI-команда (процесс завершается). */
    fun run(args: Array<String>): Boolean {
        val list = args.toList()
        if (list.isEmpty()) return false
        return when (list.first()) {
            "type" -> {
                val (device, rest) = parseDevice(list.drop(1))
                if (rest.isEmpty()) {
                    println("usage: type [--device <path>] <text>")
                    return true
                }
                typeText(device, rest.joinToString(" "))
                true
            }

            "type-file" -> {
                val (device, rest) = parseDevice(list.drop(1))
                val file = rest.firstOrNull()
                if (file == null) {
                    println("usage: type-file [--device <path>] <file>")
                    return true
                }
                typeFile(device, file)
                true
            }

            else -> false
        }
    }

    private fun parseDevice(args: List<String>): Pair<String, List<String>> {
        if (args.size >= 2 && args[0] == "--device") {
            return args[1] to args.drop(2)
        }
        return DEFAULT_HID_DEVICE to args
    }

    private fun openKeyboard(device: String): Keyboard =
        Keyboard(
            device = openHidDevice(device),
            profile = TypingProfile(),
        )

    private fun typeText(device: String, text: String) {
        val keyboard = openKeyboard(device)
        try {
            runBlocking { keyboard.type(text) }
        } finally {
            keyboard.close()
        }
    }

    private fun typeFile(device: String, fileName: String) {
        val path = Path(fileName)
        if (!SystemFileSystem.exists(path)) {
            println("Файл не найден: $fileName")
            return
        }
        val text = SystemFileSystem.source(path).buffered().use { it.readString() }
        typeText(device, text)
    }

    fun printHelp() {
        println(
            """
            Имитация ввода с клавиатуры:
              type [--device <path>] <text>   напечатать текст
              type-file [--device <path>] <f> напечатать содержимое файла
            Устройство по умолчанию: $DEFAULT_HID_DEVICE
            """.trimIndent(),
        )
    }
}
