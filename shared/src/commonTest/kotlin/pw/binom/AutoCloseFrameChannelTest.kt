package pw.binom

import pw.binom.testing.Testing
import pw.binom.testing.shouldBeFalse
import pw.binom.testing.shouldBeTrue
import pw.binom.testing.shouldContentEquals
import pw.binom.testing.shouldEquals
import kotlin.random.Random
import kotlin.test.Test

class AutoCloseFrameChannelTest {

    val id = 4000
    val BUFFER_LEN = 40
    val idBytes = id.toByteArray()
    val DATA = Array(10) {
        Random.nextBytes(BUFFER_LEN)
    }

    /**
     * Читаем как обычно пока не дойдём до конца
     */
    @Test
    fun readToClose() = Testing.async {
        val channel = TestingFrameChannel()
        channel.pushInput {
            !AutoCloseFrameChannel.DATA
            !id
            !DATA[0]
        }
        channel.pushInput {
            !AutoCloseFrameChannel.DATA
            !id
            !DATA[1]
        }
        channel.pushInput {
            !AutoCloseFrameChannel.CLOSE
            !id
        }
        channel.pushInput {
            !AutoCloseFrameChannel.DATA
            !id
            !DATA[2]
        }
        val autoCloseChannel = AutoCloseFrameChannel(channel, id)
        autoCloseChannel.isClosed.shouldBeFalse()
        autoCloseChannel.readFrame { it.readByteArray(BUFFER_LEN) }.getOrThrow() shouldContentEquals DATA[0]
        autoCloseChannel.isClosed.shouldBeFalse()
        autoCloseChannel.readFrame { it.readByteArray(BUFFER_LEN) }.getOrThrow() shouldContentEquals DATA[1]
        autoCloseChannel.isClosed.shouldBeFalse()
        autoCloseChannel.readFrame { it.readByteArray(BUFFER_LEN) }.isClosed.shouldBeTrue()
        autoCloseChannel.isClosed.shouldBeTrue()
//        channel.wasReadPackages shouldEquals 3
    }

    @Test
    fun closeAndSkipUntilEnd() = Testing.async {
        val channel = TestingFrameChannel()
        channel.pushInput {
            !AutoCloseFrameChannel.DATA
            !id
            !DATA[0]
        }
        channel.pushInput {
            !AutoCloseFrameChannel.DATA
            !id
            !DATA[1]
        }
        channel.pushInput {
            !AutoCloseFrameChannel.CLOSE
            !id
        }
        channel.pushInput {
            !AutoCloseFrameChannel.DATA
            !(id + 1)
            !DATA[2]
        }
        val autoCloseChannel = AutoCloseFrameChannel(channel, id)
        autoCloseChannel.readFrame { it.readByteArray(BUFFER_LEN) }.getOrThrow() shouldContentEquals DATA[0]
        autoCloseChannel.isClosed.shouldBeFalse()
        autoCloseChannel.asyncClose()
        channel.popOut() shouldContentEquals byteArrayOf(AutoCloseFrameChannel.CLOSE, *idBytes)
//        channel.wasReadPackages shouldEquals 3
    }
}
