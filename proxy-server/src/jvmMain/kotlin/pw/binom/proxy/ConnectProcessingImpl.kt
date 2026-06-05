package pw.binom.proxy

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import pw.binom.channel.TcpConnectChannel
import kotlin.time.Duration.Companion.seconds

class ConnectProcessingImpl(
    private val tcpConnectChannel: TcpConnectChannel,
) : ConnectProcessing {
    val logger = KotlinLogging.logger {}
    override suspend fun connect(host: String, port: Int, context: ProxyingRawContext) {

        val channel = withTimeoutOrNull(5.seconds) {
            tcpConnectChannel.connect(
                host = host,
                port = port,
            )
        }
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
