package pw.binom.subchannel

import pw.binom.*
import pw.binom.frame.FrameChannel
import pw.binom.io.ByteBuffer
import pw.binom.io.nextBytes
import pw.binom.io.use
import pw.binom.io.useAsync
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

object SpeedTestServer {
    suspend fun processing(channel: FrameChannel) {
        channel.useAsync { channel ->
            channel.sendFrame { it.writeByte(WorkerChanelClient.CONNECTED) }
            val cmd = channel.readFrame {
                it.readByte() to it.readLong().milliseconds
            }.valueOrNull ?: return
            when (cmd.first) {
                SpeedTestClient.UPLOAD_TEST -> uploadTest(channel)
                SpeedTestClient.DOWNLOAD_TEST -> testDownload(channel = channel, time = cmd.second)
                else -> {}
            }
        }
    }

    private suspend fun uploadTest(channel: FrameChannel) {
        while (true) {
            val cmd = channel.readFrame {
                it.readByte()
            }.valueOrNull ?: break
            if (cmd == SpeedTestClient.FINISH) {
                break
            }
        }
    }

    suspend fun testDownload(channel: FrameChannel, time: Duration) {
        val mt = TimeSource.Monotonic.markNow()
        channel.useAsync { channel ->
            byteBuffer(channel.bufferSize.asInt - 1).use { buffer ->
                while (mt.elapsedNow() < time) {
                    buffer.clear()
                    Random.nextBytes(buffer)
                    channel.sendFrame {
                        it.writeByte(SpeedTestClient.DATA)
                        it.writeFrom(buffer)
                    }.valueOrNull ?: break
                }
            }
            channel.sendFrame {
                it.writeByte(SpeedTestClient.FINISH)
            }
        }
    }
}
