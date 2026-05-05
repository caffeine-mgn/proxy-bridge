package pw.binom.channel

import io.klogging.logger
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.io.Buffer
import kotlinx.io.readString
import kotlinx.io.writeString
import org.koin.dsl.bind
import org.koin.dsl.module
import pw.binom.multiplexer.DuplexChannel
import pw.binom.multiplexer.MultiplexerImpl
import pw.binom.multiplexer.lebInt
import pw.binom.multiplexer.lebString
import pw.binom.utils.send

object TcpConnectChannel : ChannelHandler {
    const val ID: Byte = 1

    private val logger = logger(this::class)
    val module = module {
        single { TcpConnectChannel } bind ChannelHandler::class
    }

    suspend fun connect(
        host: String,
        port: Int,
        multiplexer: MultiplexerImpl,
    ): DuplexChannel? {
        val channel = multiplexer.createChannel()
        val b = Buffer()
        b.writeByte(ID)
        b.lebString(host)
        b.lebInt(port)
        channel.send(b)
        val buffer = channel.receive()
        val ok = buffer.readByte()
        return if (ok == 0.toByte()) {
            val error = buffer.readString()
            val stacktrace = buffer.readString()
            null
        } else {
            channel
        }
    }

    override val id: Byte
        get() = ID

    override suspend fun income(selector: SelectorManager, channel: DuplexChannel, buffer: Buffer) {
        val host = buffer.lebString()
        val port = buffer.lebInt()
        logger.info { "Connect to \"$host:$port\"" }
        val socket = try {
            aSocket(selector).tcp().connect(host, port)
        } catch (e: Throwable) {
            channel.send {
                writeByte(0)
                writeString(e.toString())
                writeString(e.stackTraceToString())
            }
            logger.warn { "Can't connect to \"$host:$port\":${e.stackTraceToString()}" }
            return
        }

        channel.send {
            writeByte(1)
        }
        val socketIncome = socket.openReadChannel()
        val socketOutcome = socket.openWriteChannel()

        try {
            pw.binom.utils.connect(
                outcome = channel.outcome,
                income = channel.income,
                a = socketOutcome,
                b = socketIncome,
            )
        } finally {
            channel.income.cancel()
            channel.outcome.close()
        }
    }
}
