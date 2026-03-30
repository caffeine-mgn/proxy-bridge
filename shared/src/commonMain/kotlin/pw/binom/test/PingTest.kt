package pw.binom.test

import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.isActive
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import pw.binom.multiplexer.DuplexChannel
import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.TimeSource

object PingTest {
    suspend fun server(channel: DuplexChannel) {
        while (coroutineContext.isActive) {
            try {
                val income = channel.income.receive()
                val incomeBytes = income.readByteArray()
                val outcome = Buffer()
                outcome.write("Echo".encodeToByteArray())
                outcome.write(incomeBytes)
                channel.outcome.send(outcome)
            } catch (_: ClosedReceiveChannelException) {
                break
            }
        }
    }

    suspend fun client(channel: DuplexChannel, bodySize: Int): Duration {
//        val body = "Ping Test ${i++}".encodeToByteArray()
        val body = Random.nextBytes(bodySize)
        val outcome = Buffer()
        outcome.write(body)
        val now = TimeSource.Monotonic.markNow()
        channel.outcome.send(outcome)
        val buf = channel.income.receive()
        return now.elapsedNow()
    }
}
