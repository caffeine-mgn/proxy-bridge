package pw.binom.proxy

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import pw.binom.io.*
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ChannelBridgeTest {
    class AsyncInputWithDelay(val delay: Duration, size: Long) : AsyncInput {
        init {
            require(size >= 0)
        }

        override val available: Int
            get() = -1

        override suspend fun asyncClose() {
        }

        private var remaining = size

        override suspend fun read(dest: ByteBuffer): Int {
            val wasRead = minOf(dest.remaining, remaining.toInt())
            remaining -= wasRead
            if (delay > Duration.ZERO) {
                println("wating $delay. remaining=$remaining wasRead=$wasRead")
                delay(delay)
            }

            dest.position += wasRead
            return wasRead
        }
    }

    @Test
    fun test() =
        runTest {
            val mb5 = 1024L * 1024L * 5
            val channel =
                ChannelBridge.create(
                    id = 0,
                    local =
                        AsyncChannel.create(
                            input = AsyncInputWithDelay(1.seconds, mb5),
                            output = AsyncOutput.NULL
                        ),
                    remote =
                        AsyncChannel.create(
                            input = AsyncInputWithDelay(0.seconds, 1L),
                            output = AsyncOutput.NULL
                        ),
                    scope = GlobalScope
                )
            channel.use { it.join() }
        }
}
