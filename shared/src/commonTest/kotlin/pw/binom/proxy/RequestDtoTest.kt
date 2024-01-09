package pw.binom.proxy

import kotlin.test.Test
import kotlin.test.assertEquals

class RequestDtoTest {
    @Test
    fun serTest() {
        val g = RequestDto(connect = RequestDto.Connect(host = "123", port = 11, channelId = 22)).toByteArray()
        assertEquals(11, g.size)
    }
}
