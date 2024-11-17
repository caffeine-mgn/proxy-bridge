package pw.binom.subchannel.commands

import pw.binom.*
import pw.binom.frame.FrameChannel
import pw.binom.io.*
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.services.ClientService
import pw.binom.strong.inject
import pw.binom.subchannel.Command
import pw.binom.subchannel.commands.TcpConnectCommand.TcpClient
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.TimeSource

class BenchmarkCommand : Command<BenchmarkCommand.Client> {
    companion object {
        const val DATA: Byte = 0x1
        const val END: Byte = 0x2
        const val RESP: Byte = 0x3
    }

    private val clientService by inject<ClientService>()
    suspend fun new() = clientService.startServer(this)
    private val logger by Logger.ofThisOrGlobal

    class Client(override val channel: FrameChannel) : AbstractCommandClient() {
        private val logger by Logger.ofThisOrGlobal
        suspend fun make(time: Duration, size: Int): Long {
            val now = TimeSource.Monotonic.markNow()
            var byteSend = 0L
            ByteBuffer(size - 1).use { buf ->
                channel.useAsync { channel ->
                    while (now.elapsedNow() < time) {
                        buf.clear()
                        buf.holdState {
                            Random.nextBytes(buf)
                        }
                        byteSend += channel.sendFrame {
                            it.writeByte(DATA)
                            it.writeFrom(buf) + 1
                        }.valueOrNull ?: break ?: break
                        channel.readFrame { it.readByte() }.valueOrNull ?: break
                    }
                    channel.sendFrame {
                        it.writeByte(END)
                    }
                }
            }
            return byteSend
        }
    }

    override val cmd: Byte
        get() = 0x2

    override suspend fun startClient(channel: FrameChannel) {
        while (true) {
            logger.info("Reading message...")
            val cmd = channel.readFrame { it.readByte() }.valueOrNull ?: return
            if (cmd == END) {
                logger.info("Benchmark is finished!")
                break
            }
            channel.sendFrame { it.writeByte(RESP) }
            logger.info("Message was read! Wait next message!")
        }
    }

    override suspend fun startServer(channel: FrameChannel): Client =
        Client(channel)
}
