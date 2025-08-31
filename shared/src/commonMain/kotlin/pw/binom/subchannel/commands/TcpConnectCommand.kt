package pw.binom.subchannel.commands

import pw.binom.*
import pw.binom.frame.FrameChannel
import pw.binom.frame.FrameChannelWithMeta
import pw.binom.io.AsyncChannel
import pw.binom.io.IOException
import pw.binom.io.socket.UnknownHostException
import pw.binom.io.useAsync
import pw.binom.services.ClientService
import pw.binom.strong.inject
import pw.binom.subchannel.AbstractCommandClient
import pw.binom.subchannel.Command
import kotlin.coroutines.cancellation.CancellationException

class TcpConnectCommand : Command<TcpConnectCommand.TcpClient> {
    private val clientService by inject<ClientService>()

    companion object {
        const val CONNECTED: Byte = 0x3
        const val HOST_NOT_FOUND: Byte = 0x4
        const val UNKNOWN_ERROR: Byte = 0x5
        const val NOT_SUPPORTED: Byte = 0x6
    }

    suspend fun connect(host: String, port: Int): Connected {
        val client = clientService.startServer(this)
        return client.connect(host = host, port = port)
    }

    val tcpConnectionFactory by inject<TcpConnectionFactory>()

    class TcpClient(override val channel: FrameChannelWithMeta) : AbstractCommandClient() {

        fun channel() = asClosed { channel }

        @Throws(UnknownHostException::class, IOException::class, CancellationException::class)
        suspend fun connect(host: String, port: Int): Connected = asClosed {
            channel.meta["type"] = "tcp source connection"
            channel.meta["tcp"] = "$host:$port"
            channel.sendFrame {
                it.writeString(host)
                it.writeInt(port)
            }
            val result = channel.readFrame {
                when (val r = it.readByte()) {
                    CONNECTED -> null
                    HOST_NOT_FOUND -> UnknownHostException()
                    UNKNOWN_ERROR -> IOException(it.readString())
                    else -> IOException("Unknown code 0x${r.toUByte().toString(16)}")
                }
            }
            if (result.isClosed) {
                throw IOException("Connection closed")
            }
            result.getOrThrow()?.let { throw it }
            Connected(channel)
        }
    }

    class Connected(override val channel: FrameChannelWithMeta) : AbstractCommandClient() {
        fun channel() = asClosed { channel }
        suspend fun startExchange(stream: AsyncChannel) {
            channel.meta["type"] = "tcp source connected"
            val copyResult = asClosed {
                channel.useAsync { channel ->
                    stream.useAsync { stream ->
                        Cooper.exchange(
                            stream = stream,
                            frame = channel,
                            channelName = "TCP destination",
                        )
                    }
                }
            }
            println("Coping finished: $copyResult")
        }
    }

    override val cmd: Byte
        get() = Command.TCP_CONNECT

    override suspend fun startClient(channel: FrameChannelWithMeta) {
        channel.meta["type"] = "tcp destination connecting"
        channel.useAsync { channel ->
            val (host, port) = channel.readFrame {
                it.readString() to it.readInt()
            }.valueOrNull ?: return
            val stream = try {
                tcpConnectionFactory.connect(
                    host = host,
                    port = port,
                )
            } catch (_: UnknownHostException) {
                channel.meta["type"] = "tcp destination unknown host"
                channel.sendFrame {
                    it.writeByte(HOST_NOT_FOUND)
                }
                return
            } catch (e: Throwable) {
                channel.meta["type"] = "tcp destination unknown error"
                channel.sendFrame {
                    it.writeByte(UNKNOWN_ERROR)
                    it.writeString(e.message ?: e.toString())
                }
                return
            }
            channel.meta["type"] = "tcp destination connected"
            channel.meta["tcp"] = "$host:$port"
            channel.sendFrame {
                it.writeByte(CONNECTED)
            }
            val copyResult = stream.useAsync { stream ->
                Cooper.exchange(
                    stream = stream,
                    frame = channel,
                    channelName = "TCP source"
                )
            }
            println("Coping finished: $copyResult")
        }
    }

    override suspend fun startServer(channel: FrameChannelWithMeta): TcpClient = TcpClient(channel)
}
