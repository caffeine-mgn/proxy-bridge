package pw.binom

import kotlin.test.Test
import kotlin.test.assertEquals

class InputStreamExtensionsTest {
    @Test
    fun aa() {
        val int = 255
        val e = int.toUByte()
        val v = e.toUByte()
        assertEquals(UByte.MAX_VALUE, e)
//        assertEquals(Byte.MAX_VALUE, e)
        println("e=$e ${e.toByte()}")
    }


}
