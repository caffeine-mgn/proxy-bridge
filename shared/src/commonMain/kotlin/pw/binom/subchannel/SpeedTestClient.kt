package pw.binom.subchannel

import pw.binom.*
import pw.binom.atomic.AtomicBoolean
import pw.binom.frame.FrameChannel
import pw.binom.io.ByteBuffer
import pw.binom.io.nextBytes
import pw.binom.io.use
import pw.binom.io.useAsync
import kotlin.math.PI
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class SpeedTestClient(val channel: FrameChannel) {
    companion object {
        const val UPLOAD_TEST: Byte = 0x1
        const val DOWNLOAD_TEST: Byte = 0x2
        const val DATA: Byte = 0x3
        const val FINISH: Byte = 0x4
        const val PING: Byte = 0x5
        const val PONG: Byte = 0x6

        suspend fun pingTest(channel: FrameChannel, times: Int): Duration? = measureTime {
            require(times > 0) { "Ping times shouldn't be less than 0!" }
            repeat(times) {
                channel.sendFrame {
                    it.writeByte(PING)
                }
                val cmd = channel.readFrame { it.readByte() }.valueOrNull ?: return null
                if (cmd != PONG) return null
            }
        } / times


        suspend fun sendAndWait(channel: FrameChannel) {
            channel.sendFrame {
                it.writeByte(DATA)
            }
        }
    }

    private val closed = AtomicBoolean(false)
    private fun makeClose() {
        check(closed.compareAndSet(false, true)) { "Already closed." }
    }

    suspend fun pingTest(times: Int): Duration? {
        makeClose()
        channel.useAsync { channel ->
            channel.sendFrame {
                it.writeByte(PING)
                it.writeInt(times)
            }
            val cmd = channel.readFrame { it.readByte() }.valueOrNull ?: return null
            if (cmd != WorkerChanelClient.CONNECTED) {
                return null
            }
            return pingTest(channel = channel, times = times)
        }
    }

    suspend fun testUpload(time: Duration) = measureTimedValue {
        var data = 0L
        channel.useAsync { channel ->
            channel.sendFrame {
                it.writeByte(UPLOAD_TEST)
                it.writeLong(time.inWholeMilliseconds)
            }
            val mt = TimeSource.Monotonic.markNow()
            byteBuffer(channel.bufferSize.asInt - 1).use { buffer ->
                while (mt.elapsedNow() < time) {
                    buffer.clear()
                    Random.nextBytes(buffer)
                    val count = channel.sendFrame {
                        it.writeByte(DATA)
                        it.writeFrom(buffer) + 1
                    }.valueOrNull ?: break
                    data += count
                }
            }
            channel.sendFrame {
                it.writeByte(FINISH)
            }
        }
        data
    }

    suspend fun testDownload(time: Duration) = measureTimedValue {
        var data = 0L
        channel.useAsync { channel ->
            channel.sendFrame {
                it.writeByte(DOWNLOAD_TEST)
                it.writeLong(time.inWholeMilliseconds)
            }
            byteBuffer(channel.bufferSize.asInt - 1).use { buffer ->
                while (true) {
                    buffer.clear()
                    val cmd = channel.readFrame {
                        val cmd = it.readByte()
                        val len = it.readInto(buffer) + 1
                        cmd to len
                    }.valueOrNull ?: break
                    if (cmd.first == FINISH) {
                        break
                    }
                    data += cmd.second
                }
            }
        }
        data
    }
}
