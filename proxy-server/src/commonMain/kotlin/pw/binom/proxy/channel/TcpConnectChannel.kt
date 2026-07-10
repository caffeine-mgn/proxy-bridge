package pw.binom.proxy.channel

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.selector.SelectorManager
import kotlinx.io.Buffer
import kotlinx.io.readString
import kotlinx.io.writeString
import org.koin.dsl.bind
import org.koin.dsl.module
import pw.binom.proxy.services.TcpConnectProvider
import pw.binom.multiplexer.DuplexChannel
import pw.binom.multiplexer.Multiplexer
import pw.binom.multiplexer.lebInt
import pw.binom.multiplexer.lebString
import pw.binom.utils.send

class TcpConnectChannel(
    val selector: SelectorManager,
    val multiplexer: Multiplexer,
    val tcpConnectProvider: TcpConnectProvider,
) : ChannelHandler {
    companion object {
        private val logger = KotlinLogging.logger { }
        const val ID: Byte = 1
        val module = module {
            single { TcpConnectChannel(get(), get(), get()) } bind ChannelHandler::class
        }

        suspend fun connect(
            channel: DuplexChannel,
            host: String,
            port: Int,
        ): DuplexChannel? {
            val b = Buffer()
            b.writeByte(ID)
            b.lebString(host)
            b.lebInt(port)
            println("SEND CONNECT $host:$port")
            channel.send(b)
            val buffer = channel.receive()
            val ok = buffer.readByte()
            println("OK CONNECT: $ok")
            return if (ok == 0.toByte()) {
                val error = buffer.readString()
                val stacktrace = buffer.readString()
//                logger.info { "Can't connect to \"$host:$port\":$error\n$stacktrace" }
                logger.info { "Can't connect to \"$host:$port\":$error" }
                null
            } else {
                channel
            }
        }
    }

    private val logger = KotlinLogging.logger { }


    override val id: Byte
        get() = ID

    override suspend fun income(channel: DuplexChannel, buffer: Buffer) {
        val host = buffer.lebString()
        val port = buffer.lebInt()
        logger.info { "TcpConnectChannel::income Connect to \"$host:$port\"" }
        val socket = try {
            tcpConnectProvider.connect(host, port)
//            aSocket(selector).tcp().connect(host, port)
        } catch (e: Throwable) {
            TcpConnectProvider.ConnectResult.UnknownError
        }
        if (socket !is TcpConnectProvider.ConnectResult.Success) {
            val msg = (socket as? TcpConnectProvider.ConnectResult.Error)?.msg
            channel.send {
                writeByte(0)
                writeString(msg ?: "unknown error")
                writeString("none")
            }
            logger.error { "TcpConnectChannel::income Can't connect to \"$host:$port\":$msg" }
            return
        }
        logger.info { "TcpConnectChannel::income Connected success to \"$host:$port\"" }

        channel.send {
            writeByte(1)
        }
        val socketIncome = socket.readChannel
        val socketOutcome = socket.writeChannel

        try {
            pw.binom.utils.connect(
                outcome = channel.outcome,
                income = channel.income,
                a = socketOutcome,
                b = socketIncome,
            )
        } finally {
            socket.close()
            channel.income.cancel()
            channel.outcome.close()
        }
    }
}
