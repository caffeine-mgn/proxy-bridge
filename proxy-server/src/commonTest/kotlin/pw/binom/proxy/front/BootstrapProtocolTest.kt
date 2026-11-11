package pw.binom.proxy.front

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Раскладка кадра фиксирована протоколом исходного бутстрапа: приёмник на
 * удалённой машине ожидает именно такие поля и порядок байт.
 */
class BootstrapProtocolTest {
    @Test
    fun frameLayout() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val frame = BootstrapProtocol.frame("a.bin", payload)

        assertEquals(BootstrapProtocol.START_FILE.toByte(), frame[0])

        // nameLen: 5 байт, big-endian
        assertEquals(0, frame[1].toInt())
        assertEquals(0, frame[2].toInt())
        assertEquals(0, frame[3].toInt())
        assertEquals(5, frame[4].toInt())

        assertEquals("a.bin", frame.copyOfRange(5, 10).decodeToString())

        // size: 5 байт, big-endian
        assertEquals(0, frame[10].toInt())
        assertEquals(0, frame[11].toInt())
        assertEquals(0, frame[12].toInt())
        assertEquals(5, frame[13].toInt())

        assertEquals(payload.toList(), frame.copyOfRange(14, 19).toList())
        assertEquals(19, frame.size)
    }

    @Test
    fun largeSizeIsBigEndian() {
        val frame = BootstrapProtocol.frame("x", ByteArray(0x01020304))
        // START_FILE + nameLen(4) + name(1) = 6 байт перед размером.
        assertEquals(0x01, frame[6].toInt())
        assertEquals(0x02, frame[7].toInt())
        assertEquals(0x03, frame[8].toInt())
        assertEquals(0x04, frame[9].toInt())
    }
}
