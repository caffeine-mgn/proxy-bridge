package pw.binom.proxy.serialization

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalSerializationApi::class)
class ShortTest {
    @Serializable
    data class VVV(val first: Boolean, val second: Boolean?)

    @Serializable
    data class User(val name: String, val value: VVV?)

    @Serializable
    data class IntValue(val f: Int)

    inline fun <reified T : Any> test(value: T) {
        test(
            T::class.serializer(),
            value
        )
    }

    fun <T> test(
        serializer: KSerializer<T>,
        value: T,
    ) {
        val bytes = ShortSerialization.encodeByteArray(serializer, value)
        val o = ShortSerialization.decodeByteArray(serializer, bytes)
        assertEquals(value, o)
    }

//    @Test
//    fun test() {
//        test(RequestDto.serializer(), RequestDto(connect = RequestDto.Connect(host = "123", port = 11, channelId = 22)))
//    }

    @Test
    fun intTest() {
        intArrayOf(-127, 128, -32767, 32768).forEach {
            test(it)
            test(IntValue(-it))
        }
    }
}
