package pw.binom.subchannel

import kotlinx.coroutines.withTimeoutOrNull
import pw.binom.frame.FrameChannel
import pw.binom.atomic.AtomicBoolean
import pw.binom.io.AsyncCloseable
import pw.binom.io.socket.UnknownHostException
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.infoSync
import kotlin.time.Duration.Companion.seconds

class WorkerChanelClient(val channel: FrameChannel) : AsyncCloseable {
    companion object {
        const val CLOSE: Byte = 0x1
        const val START_TCP: Byte = 0x2
        const val FS_ACCESS: Byte = 0xa
        const val CONNECTED: Byte = 0x3
        const val HOST_NOT_FOUND: Byte = 0x4
        const val UNKNOWN_ERROR: Byte = 0x5
        const val NOT_SUPPORTED: Byte = 0x6
    }

    private val closed = AtomicBoolean(false)
    private val logger by Logger.ofThisOrGlobal

    suspend fun fs(): FSClient {
        channel.sendFrame {
            it.writeByte(FS_ACCESS)
        }.ensureNotClosed()
        if (channel.readFrame { it.readByte() }.valueOrNull == CONNECTED) {
            TODO()
        }
        closed.setValue(true)
        return FSClient(channel)
    }

    suspend fun startTcp(host: String, port: Int): TcpExchange {
        logger.info("Start tcp exchange for $host:$port")
        try {
            channel.sendFrame {
                it.writeByte(START_TCP)
                it.writeString(host)
                it.writeInt(port)
            }.ensureNotClosed { "Can't send message to $channel" }
            val c = withTimeoutOrNull(10.seconds) {
                channel.readFrame {
                    val cmd = it.readByte()
                    when (cmd) {
                        CONNECTED -> channel
                        HOST_NOT_FOUND -> throw UnknownHostException(host)
                        UNKNOWN_ERROR -> throw RuntimeException(it.readString())
                        NOT_SUPPORTED -> throw RuntimeException("Not supported")
                        else -> throw IllegalStateException("Unknown command $cmd (0x${cmd.toUByte().toString(16)}")
                    }
                }
            }?.ensureNotClosed() ?: TODO("Timeout on $channel")
            closed.setValue(true)
            logger.info("Start TCP session")
            return TcpExchange(c)
        } catch (e: Throwable) {
            asyncCloseAnyway()
            throw e
        }
    }

    override suspend fun asyncClose() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        channel.sendFrame {
            it.writeByte(CLOSE)
        }
    }

}
