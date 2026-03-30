package pw.binom.test

import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.isActive
import kotlinx.io.Buffer
import pw.binom.multiplexer.DuplexChannel
import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.TimeSource

object ThroughputTest {
    suspend fun server(channel: DuplexChannel, ack: Boolean) {
        while (coroutineContext.isActive) {
            try {
                channel.income.receive()
                if (ack) {
                    val outcome = Buffer()
                    outcome.write("OK".encodeToByteArray())
                    channel.outcome.send(outcome)
                }
            } catch (_: ClosedReceiveChannelException) {
                break
            }
        }
    }

    suspend fun client(channel: DuplexChannel, bodySize: Int = 512, time: Duration, ack: Boolean): Long {
        val now = TimeSource.Monotonic.markNow()
        val buffer = Random.nextBytes(bodySize)
        var len = 0L
        while (now.elapsedNow() < time) {
            val outcome = Buffer()
            outcome.write(buffer)
            channel.outcome.send(outcome)
            if (ack) {
                channel.income.receive()
            }
            len += bodySize
        }
        return len
    }
}
