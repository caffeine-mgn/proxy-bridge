package pw.binom.channel

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.io.Buffer
import kotlinx.io.readString
import kotlinx.io.writeString
import pw.binom.multiplexer.DuplexChannel
import pw.binom.multiplexer.Multiplexer
import pw.binom.multiplexer.lebInt
import pw.binom.multiplexer.lebString
import pw.binom.utils.send

object TcpConnectChannel {
    const val ID: Byte = 1
    suspend fun connect(
        host: String,
        port: Int,
        multiplexer: Multiplexer,
    ): DuplexChannel? {
        val channel = multiplexer.createChannel()
        val b = Buffer()
        b.writeByte(ID)
        b.lebString(host)
        b.lebInt(port)
        println("Sending ${b.size}")
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

    suspend fun income(selector: SelectorManager, channel: DuplexChannel, buffer: Buffer) {
        println("Income buffer. Size: ${buffer.size}")
        val host = buffer.lebString()
        println("Host: \"$host\"")
        val port = buffer.lebInt()
        println("Port: $port")
        val socket = try {
            aSocket(selector).tcp().connect(host, port)
        } catch (e: Throwable) {
            channel.send {
                writeByte(0)
                writeString(e.toString())
                writeString(e.stackTraceToString())
            }
            e.printStackTrace()
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
