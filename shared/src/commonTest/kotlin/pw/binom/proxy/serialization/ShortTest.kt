package pw.binom.proxy.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import pw.binom.proxy.RequestDto
import kotlin.test.Test
import kotlin.test.assertEquals

class ShortTest {
    @Serializable
    data class VVV(val first: Boolean, val second: Boolean?)

    @Serializable
    data class User(val name: String, val value: VVV?)

    fun <T> test(
        serializer: KSerializer<T>,
        value: T,
    ) {
        val bytes = ShortSerialization.encodeByteArray(serializer, value)
        println("->${bytes.toList()}->${bytes.size}")
        val o = ShortSerialization.decodeByteArray(serializer, bytes)
        assertEquals(value, o)
    }

    @Test
    fun test() {
//        test(User.serializer(), User(name = "Anton", value = null))
        val arr = RequestDto(connect = RequestDto.Connect(host = "123", port = 11, channelId = 22)).toByteArray()
        println("  ${arr.toList()} ${arr.size}")
        test(RequestDto.serializer(), RequestDto(connect = RequestDto.Connect(host = "123", port = 11, channelId = 22)))
    }
}
