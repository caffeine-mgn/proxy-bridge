package pw.binom.hid

import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Ввод «с клавиатуры» в HID-устройство.
 *
 * Умеет печатать текст и отдельные сочетания клавиш. Каждый символ — это два
 * отчёта: нажатие и отпускание (как у настоящей клавиатуры), между ними и после
 * них выдерживаются паузы из [TypingProfile].
 *
 * Не является потокобезопасным: одновременный ввод из двух мест даст мусор в
 * отчётах. Для параллельных сценариев — своя блокировка снаружи.
 */
class Keyboard(
    private val device: HidDevice,
    private val layout: KeyboardLayout = UsLayout,
    private val profile: TypingProfile = TypingProfile(),
) {
    private val random = profile.seed?.let { Random(it) } ?: Random.Default
    private val report = ByteArray(device.reportLength)

    /** Пустой отчёт: все клавиши отпущены. */
    private fun releaseAllReport() {
        report.fill(0)
        device.writeReport(report)
    }

    /** Нажатие одной клавиши с отпусканием. */
    suspend fun press(stroke: KeyStroke) {
        writeReport(stroke)
        delay(profile.holdMs.coerceAtLeast(1).toLong())
        releaseAllReport()
        delay(nextDelay(stroke))
    }

    suspend fun press(key: Char) {
        press(mapOrThrow(key))
    }

    /** Печатает текст символ за символом. */
    suspend fun type(text: String) {
        for (char in text) {
            press(char)
        }
    }

    /**
     * Модификатор + клавиша (Ctrl+C, Alt+F4, Win+R и т.п.).
     * Модификатор отпускается вместе с клавишей.
     */
    suspend fun shortcut(modifiers: Int, usage: Int) {
        press(KeyStroke(usage = usage, modifiers = modifiers))
    }

    /**
     * Печатает текст «по строкам»: полезно для скриптов, где после Enter
     * среда может начать выполнение команды и ей нужна пауза.
     */
    suspend fun typeLines(text: String, afterLineDelayMs: Int = 250) {
        val lines = text.split('\n')
        lines.forEachIndexed { index, line ->
            if (line.isNotEmpty()) type(line)
            if (index != lines.lastIndex) {
                press('\n')
                delay(afterLineDelayMs.toLong())
            }
        }
    }

    fun close() = device.close()

    private fun mapOrThrow(char: Char): KeyStroke =
        layout.map(char) ?: throw UnsupportedCharacterException(char)

    private fun writeReport(stroke: KeyStroke) {
        report.fill(0)
        report[0] = stroke.modifiers.toByte()
        report[1] = 0
        report[2] = stroke.usage.toByte()
        device.writeReport(report)
    }

    private fun nextDelay(stroke: KeyStroke): Long {
        var ms = profile.charDelayMs
        if (profile.jitterMs > 0) ms += random.nextInt(profile.jitterMs)
        if (stroke.usage == KeyStroke.KEY_ENTER) {
            ms += profile.newlineExtraMs
        } else if (stroke.modifiers != 0 || stroke.usage !in 0x04..0x27) {
            // Пунктуация/Shift-варианты — «дороже» обычной буквы.
            ms += profile.punctuationExtraMs
        }
        return ms.coerceAtLeast(0).toLong()
    }
}

class UnsupportedCharacterException(val char: Char) :
    IllegalArgumentException("Character '${char}' (U+${char.code.toString(16).uppercase()}) is not supported by layout")
