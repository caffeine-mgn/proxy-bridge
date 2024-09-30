package pw.binom

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pw.binom.io.EOFException
import pw.binom.io.writeByteArray
import pw.binom.testing.Testing
import pw.binom.testing.shouldBeFalse
import pw.binom.testing.shouldBeTrue
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class TestingChannelTest {

    @Test
    fun closeDuringClose() = Testing.async {
        val channel = VirtualChannel()
        var elfHappened = false
        GlobalScope.launch {
            try {
                channel.readByteArray(10)
            } catch (_: EOFException) {
                elfHappened = true
            }
        }
        channel.internal {
            writeByteArray(ByteArray(5))
        }
        delay(1.seconds)
        elfHappened.shouldBeTrue()
    }

    @Test
    fun closeTestInternal() = Testing.async {
        val channel = VirtualChannel()
        var elfHappened = false
        GlobalScope.launch {
            channel.internal {
                try {
                    readByteArray(10)
                } catch (_: EOFException) {
                    elfHappened = true
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
        delay(1.seconds)
        elfHappened.shouldBeFalse()
        channel.asyncClose()
        delay(0.5.seconds)
        elfHappened.shouldBeTrue()
    }

    @Test
    fun closeTestExternal() = Testing.async {
        val channel = VirtualChannel()
        var elfHappened = false
        GlobalScope.launch {
            try {
                channel.readByteArray(10)
            } catch (_: EOFException) {
                elfHappened = true
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        delay(1.seconds)
        elfHappened.shouldBeFalse()
        channel.asyncClose()
        delay(0.5.seconds)
        elfHappened.shouldBeTrue()
    }
}
