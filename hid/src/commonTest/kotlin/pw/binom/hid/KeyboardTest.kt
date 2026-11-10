package pw.binom.hid

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Записывает отчёты в память — вместо /dev/hidg0. */
private class FakeHidDevice(override val reportLength: Int = 8) : HidDevice {
    val reports = mutableListOf<List<Int>>()

    override fun writeReport(report: ByteArray) {
        report.requireReportLength(reportLength, "fake")
        reports += report.map { it.toInt() and 0xFF }
    }

    override fun close() = Unit
}

class UsLayoutTest {
    @Test
    fun lettersAndDigits() {
        assertEquals(KeyStroke(0x04), UsLayout.map('a'))
        assertEquals(KeyStroke(0x1D), UsLayout.map('z'))
        assertEquals(KeyStroke(0x1E), UsLayout.map('1'))
        assertEquals(KeyStroke(0x27), UsLayout.map('0'))
        assertEquals(KeyStroke(0x04, KeyStroke.MOD_LEFT_SHIFT), UsLayout.map('A'))
        assertEquals(KeyStroke(0x1E, KeyStroke.MOD_LEFT_SHIFT), UsLayout.map('!'))
    }

    @Test
    fun punctuation() {
        assertEquals(KeyStroke(0x2D), UsLayout.map('-'))
        assertEquals(KeyStroke(0x2D, KeyStroke.MOD_LEFT_SHIFT), UsLayout.map('_'))
        assertEquals(KeyStroke(0x2F, KeyStroke.MOD_LEFT_SHIFT), UsLayout.map('{'))
        assertEquals(KeyStroke(0x38, KeyStroke.MOD_LEFT_SHIFT), UsLayout.map('?'))
        assertEquals(KeyStroke(0x31), UsLayout.map('\\'))
    }

    @Test
    fun specialKeys() {
        assertEquals(KeyStroke.KEY_ENTER, UsLayout.map('\n')?.usage)
        assertEquals(KeyStroke.KEY_TAB, UsLayout.map('\t')?.usage)
        assertEquals(KeyStroke.KEY_SPACE, UsLayout.map(' ')?.usage)
    }

    @Test
    fun unsupportedCharacterIsNotMapped() {
        assertEquals(null, UsLayout.map('ж'))
        assertEquals(null, UsLayout.map('€'))
    }

    @Test
    fun typingUnsupportedCharacterFails() = runTest {
        val keyboard = Keyboard(FakeHidDevice(), profile = TypingProfile(charDelayMs = 0, jitterMs = 0, holdMs = 1))
        val error = assertFailsWith<UnsupportedCharacterException> { keyboard.press('ж') }
        assertEquals('ж', error.char)
    }
}

class KeyboardTest {
    @Test
    fun typingSendsPressAndReleasePerCharacter() = runTest {
        val device = FakeHidDevice()
        val keyboard = Keyboard(device, profile = TypingProfile(charDelayMs = 0, jitterMs = 0, holdMs = 1))

        keyboard.type("aA")

        // 2 символа × (нажатие + отпускание).
        assertEquals(4, device.reports.size)
        assertEquals(listOf(0, 0, 0x04, 0, 0, 0, 0, 0), device.reports[0])
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, 0), device.reports[1])
        assertEquals(listOf(0x02, 0, 0x04, 0, 0, 0, 0, 0), device.reports[2])
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, 0), device.reports[3])
    }

    @Test
    fun shortcutSetsModifiers() = runTest {
        val device = FakeHidDevice()
        val keyboard = Keyboard(device, profile = TypingProfile(charDelayMs = 0, jitterMs = 0, holdMs = 1))

        keyboard.shortcut(KeyStroke.MOD_LEFT_CTRL, 0x06) // Ctrl+C

        assertEquals(listOf(0x01, 0, 0x06, 0, 0, 0, 0, 0), device.reports.first())
    }

    @Test
    fun typeLinesAddsEnterBetweenLines() = runTest {
        val device = FakeHidDevice()
        val keyboard = Keyboard(device, profile = TypingProfile(charDelayMs = 0, jitterMs = 0, holdMs = 1))

        keyboard.typeLines("ab\ncd", afterLineDelayMs = 0)

        val usages = device.reports.filter { it[2] != 0 }.map { it[2] }
        assertEquals(listOf(0x04, 0x05, KeyStroke.KEY_ENTER, 0x06, 0x07), usages)
    }

    @Test
    fun everyAsciiCharacterIsTypable() {
        val missing = (32..126).map { it.toChar() }.filter { UsLayout.map(it) == null }
        assertTrue(missing.isEmpty(), "unsupported ASCII characters: $missing")
    }

    @Test
    fun reportLengthIsEnforced() {
        val device = FakeHidDevice(reportLength = 8)
        assertFailsWith<IllegalArgumentException> {
            device.writeReport(ByteArray(4))
        }
    }
}
