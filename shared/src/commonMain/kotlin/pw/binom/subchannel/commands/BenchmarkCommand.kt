package pw.binom.subchannel.commands

import pw.binom.*
import pw.binom.frame.FrameChannel
import pw.binom.io.*
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.services.ClientService
import pw.binom.strong.inject
import pw.binom.subchannel.AbstractCommandClient
import pw.binom.subchannel.Command
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class BenchmarkCommand : Command<BenchmarkCommand.Client> {
    companion object {
        const val UPLOAD_TEST: Byte = 0x1
        const val DOWNLOAD_TEST: Byte = 0x2
        const val PING_TEST: Byte = 0x3
        const val DATA_WITH_RESP: Byte = 0x4
        const val DATA_WITHOUT_RESP: Byte = 0x5
        const val END: Byte = 0x6
        const val RESP: Byte = 0x7
    }

    private val clientService by inject<ClientService>()
    suspend fun new() = clientService.startServer(this)
    private val logger by Logger.ofThisOrGlobal

    class Client(override val channel: FrameChannel) : AbstractCommandClient() {
        private val logger by Logger.ofThisOrGlobal
        suspend fun uploadTest(time: Duration, size: Int, withResp: Boolean): Long =
            asClosed {
                var byteSend = 0L
                channel.useAsync { channel ->
                    channel.sendFrame { it.writeByte(UPLOAD_TEST) }
                    val now = TimeSource.Monotonic.markNow()
                    byteBuffer(size - 1).use { buf ->
                        while (now.elapsedNow() < time) {
                            buf.clear()
                            buf.holdState {
                                Random.nextBytes(buf)
                            }
                            byteSend += channel.sendFrame {
                                it.writeByte(if (withResp) DATA_WITH_RESP else DATA_WITHOUT_RESP)
                                it.writeFrom(buf) + 1
                            }.valueOrNull ?: break
                            if (withResp) {
                                channel.readFrame { it.readByte() }.valueOrNull ?: break
                            }
                        }
                        channel.sendFrame {
                            it.writeByte(END)
                        }.valueOrNull ?: TODO("Channel closed!")
                    }
                }
                return byteSend
            }

        suspend fun downloadTest(time: Duration, size: Int, withResp: Boolean): Long =
            asClosed {
                channel.useAsync { channel ->
                    channel.sendFrame {
                        it.writeByte(DOWNLOAD_TEST)
                        it.writeLong(time.inWholeMilliseconds)
                        it.writeInt(size)
                        it.writeBoolean(withResp)
                    }
                    var inputBytes = 0L
                    byteBuffer(size).use { buffer ->
                        while (true) {
                            val (cmd, size) = channel.readFrame {
                                val cmd = it.readByte()
                                cmd to it.readInto(buffer)
                            }.valueOrNull ?: break
                            if (cmd == END) {
                                logger.info("Benchmark is finished!")
                                break
                            }
                            if (cmd == DATA_WITH_RESP) {
                                channel.sendFrame { it.writeByte(RESP) }
                            }
                            inputBytes += size
                        }
                    }
                    return inputBytes
                }
            }

        suspend fun ping(count: Int): Unit = asClosed {
            channel.useAsync { channel ->
                channel.sendFrame {
                    it.writeByte(PING_TEST)
                }
                repeat(count) {
                    channel.sendFrame {
                        it.writeByte(DATA_WITH_RESP)
                    }
                }
                channel.sendFrame {
                    it.writeByte(END)
                }
            }
        }
    }

    override val cmd: Byte
        get() = pw.binom.subchannel.Command.BENCHMARK

    private sealed interface Command {
        object Upload : Command
        class Download(val time: Duration, val size: Int, val withResponse: Boolean) : Command
        object Ping : Command
    }

    override suspend fun startClient(channel: FrameChannel) {
        channel.useAsync { channel ->
            val cmd = channel.readFrame {
                when (val cmd = it.readByte()) {
                    UPLOAD_TEST -> Command.Upload
                    DOWNLOAD_TEST -> Command.Download(
                        time = it.readLong().milliseconds,
                        size = it.readInt(),
                        withResponse = it.readBoolean(),
                    )

                    PING_TEST -> Command.Ping
                    else -> TODO("Unknown cmd 0x${cmd.toUByte().toString(16)}")
                }
            }.valueOrNull ?: return
            logger.info("Income command $cmd")
            when (cmd) {
                Command.Upload -> uploadClient(channel = channel)
                is Command.Download -> downloadClient(channel = channel, cmd = cmd)
                Command.Ping -> pingClient(channel = channel)
            }
        }
    }

    private suspend fun pingClient(channel: FrameChannel) {
        while (true) {
            val cmd = channel.readFrame { it.readByte() }.valueOrNull ?: return
            if (cmd == END) {
                logger.info("Benchmark is finished!")
                break
            }
            channel.sendFrame { it.writeByte(RESP) }
        }
    }

    private suspend fun downloadClient(channel: FrameChannel, cmd: Command.Download) {
        val now = TimeSource.Monotonic.markNow()
        byteBuffer(cmd.size - 1).use { buf ->
            while (now.elapsedNow() < cmd.time) {
                buf.clear()
                buf.holdState {
                    Random.nextBytes(buf)
                }
                channel.sendFrame {
                    it.writeByte(if (cmd.withResponse) DATA_WITH_RESP else DATA_WITHOUT_RESP)
                    it.writeFrom(buf) + 1
                }.valueOrNull ?: break
                if (cmd.withResponse) {
                    channel.readFrame { it.readByte() }.valueOrNull ?: break
                }
            }
            channel.sendFrame {
                it.writeByte(END)
            }
        }
    }

    private suspend fun uploadClient(channel: FrameChannel) {
        while (true) {
            val cmd = channel.readFrame { it.readByte() }.valueOrNull ?: return
            if (cmd == END) {
                logger.info("Benchmark is finished!")
                break
            }
            if (cmd == DATA_WITH_RESP) {
                channel.sendFrame { it.writeByte(RESP) }
            }
        }
    }

    override suspend fun startServer(channel: FrameChannel): Client =
        Client(channel)
}
