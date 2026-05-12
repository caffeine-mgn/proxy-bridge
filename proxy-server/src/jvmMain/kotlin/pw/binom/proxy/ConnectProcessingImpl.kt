package pw.binom.proxy

import io.github.oshai.kotlinlogging.KotlinLogging
import pw.binom.channel.TcpConnectChannel
import pw.binom.multiplexer.MultiplexerHolder

class ConnectProcessingImpl(
    val currentMultiplexer: MultiplexerHolder,
):ConnectProcessing {
    val logger = KotlinLogging.logger {}
    override suspend fun connect(host: String, port: Int, context: ProxyingRawContext) {
        if (!currentMultiplexer.isInitialized()) {
            logger.warn { "Multiplexer is not initialized" }
            context.ioError()
            return
        }
        val multiplexer = currentMultiplexer.value
        val channel = TcpConnectChannel.connect(
            host = host,
            port = port,
            multiplexer = multiplexer
        )
        if (channel == null) {
            println("HttpProxy:: can't connect to $host:$port")
            context.notAvailable()
            return
        }
        val (read, write) = try {
            context.ok()
        } catch (_: Throwable) {
            channel.cancel()
            channel.close()
            return
        }
        try {
            pw.binom.utils.connect(
                outcome = channel.outcome,
                income = channel.income,
                a = write,
                b = read,
            )
        } catch (_: Throwable) {
            channel.cancel()
            channel.close()
        }
    }
}
