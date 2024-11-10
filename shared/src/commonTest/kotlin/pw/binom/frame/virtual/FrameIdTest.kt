package pw.binom.frame.virtual

import pw.binom.testing.shouldBeFalse
import pw.binom.testing.shouldBeTrue
import pw.binom.testing.shouldEquals
import kotlin.test.Test

class FrameIdTest {
    @Test
    fun test() {
        FrameId.INIT.next shouldEquals FrameId(1)
        FrameId.INIT.isNext(FrameId(1)).shouldBeTrue()
        FrameId.INIT.isNext(FrameId(2)).shouldBeFalse()
    }

    @Test
    fun overTest() {
        var current = FrameId.INIT
        repeat(Short.MAX_VALUE.toInt()) {
            current.isNext(current.next).shouldBeTrue("Next of $current is ${current.next}")
            current = current.next
        }
    }
}
