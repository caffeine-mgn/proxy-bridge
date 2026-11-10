package pw.binom.hid

/**
 * Одна клавиша в HID-отчёте: маска модификаторов + usage-код.
 *
 * `usage` — это **позиция клавиши** (USB HID Usage ID, таблица 0x07), а не
 * символ. Какой символ получится, решает раскладка операционной системы
 * целевой машины: в US-раскладке код 0x14 — «q», в русской — «й».
 */
data class KeyStroke(
    val usage: Int,
    val modifiers: Int = 0,
) {
    companion object {
        const val MOD_LEFT_CTRL = 0x01
        const val MOD_LEFT_SHIFT = 0x02
        const val MOD_LEFT_ALT = 0x04
        const val MOD_LEFT_GUI = 0x08
        const val MOD_RIGHT_CTRL = 0x10
        const val MOD_RIGHT_SHIFT = 0x20
        const val MOD_RIGHT_ALT = 0x40
        const val MOD_RIGHT_GUI = 0x80

        const val KEY_ENTER = 0x28
        const val KEY_ESC = 0x29
        const val KEY_BACKSPACE = 0x2A
        const val KEY_TAB = 0x2B
        const val KEY_SPACE = 0x2C
        const val KEY_CAPS_LOCK = 0x39
        const val KEY_ARROW_UP = 0x52
        const val KEY_ARROW_DOWN = 0x51
        const val KEY_ARROW_LEFT = 0x50
        const val KEY_ARROW_RIGHT = 0x4F
    }
}

/**
 * Раскладка: символ → нажатие. Пока поддерживается US — она же используется
 * в BIOS/UEFI и в консолях восстановления, то есть там, где bootstrap нужнее
 * всего. Для других раскладок достаточно добавить свой [KeyboardLayout].
 */
interface KeyboardLayout {
    fun map(char: Char): KeyStroke?
}

/**
 * US QWERTY: печатные ASCII-символы, `\n`, `\t`.
 */
object UsLayout : KeyboardLayout {
    private val unshifted = HashMap<Char, Int>()
    private val shifted = HashMap<Char, Int>()

    private fun put(char: Char, usage: Int, shift: Boolean = false) {
        if (shift) shifted[char] = usage else unshifted[char] = usage
    }

    init {
        // a-z
        for (i in 0..25) {
            put('a' + i, 0x04 + i)
            put('A' + i, 0x04 + i, shift = true)
        }
        // 1..9, 0
        val digits = "1234567890"
        for (i in digits.indices) put(digits[i], 0x1E + i)
        val shiftedDigits = "!@#$%^&*()"
        for (i in shiftedDigits.indices) put(shiftedDigits[i], 0x1E + i, shift = true)

        put('\n', KeyStroke.KEY_ENTER)
        put('\r', KeyStroke.KEY_ENTER)
        put('\t', KeyStroke.KEY_TAB)
        put(' ', KeyStroke.KEY_SPACE)

        // Пунктуация: без Shift и с ним.
        put('-', 0x2D)
        put('_', 0x2D, shift = true)
        put('=', 0x2E)
        put('+', 0x2E, shift = true)
        put('[', 0x2F)
        put('{', 0x2F, shift = true)
        put(']', 0x30)
        put('}', 0x30, shift = true)
        put('\\', 0x31)
        put('|', 0x31, shift = true)
        put(';', 0x33)
        put(':', 0x33, shift = true)
        put('\'', 0x34)
        put('"', 0x34, shift = true)
        put('`', 0x35)
        put('~', 0x35, shift = true)
        put(',', 0x36)
        put('<', 0x36, shift = true)
        put('.', 0x37)
        put('>', 0x37, shift = true)
        put('/', 0x38)
        put('?', 0x38, shift = true)
    }

    override fun map(char: Char): KeyStroke? {
        shifted[char]?.let {
            return KeyStroke(usage = it, modifiers = KeyStroke.MOD_LEFT_SHIFT)
        }
        unshifted[char]?.let {
            return KeyStroke(usage = it)
        }
        return null
    }
}

/**
 * Профиль «человеческого» ввода: паузы между символами и джиттер.
 *
 * Смысл не в красоте, а в совместимости: часть удалённых систем (терминалы,
 * KVM, прослойки вроде Citrix/RDP, экранные консоли) теряет символы, если
 * отчёты идут вплотную; а системы с key-repeat начинают дублировать. Поэтому
 * по умолчанию — пауза с разбросом, как у живого человека.
 */
data class TypingProfile(
    /** Пауза после отпускания клавиши, мс. */
    val charDelayMs: Int = 15,
    /** Разброс к паузе: реальная пауза = charDelay + [0, jitterMs). */
    val jitterMs: Int = 10,
    /** Сколько держать клавишу нажатой, мс. */
    val holdMs: Int = 8,
    /** Дополнительная пауза на пунктуацию, мс. */
    val punctuationExtraMs: Int = 10,
    /** Дополнительная пауза после Enter, мс. */
    val newlineExtraMs: Int = 40,
    /** Seed для джиттера; null — системный. */
    val seed: Int? = null,
)
