package pw.binom.proxy

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import pw.binom.TcpConnectProvider
import pw.binom.channel.TcpConnectChannel
import kotlin.time.Duration.Companion.seconds

class ConnectProcessingImpl(
    private val tcpConnectProvider: TcpConnectProvider,
) : ConnectProcessing {
    private val logger = KotlinLogging.logger {}
    override suspend fun connect(host: String, port: Int, context: ProxyingRawContext) {
        logger.info { "Connecting to $host:$port" }
        val result = tcpConnectProvider.connect(host, port)
        logger.info { "Connect result: $result" }
        val channel = when (result) {
            is TcpConnectProvider.ConnectResult.Error -> {
                context.ioError()
                return
            }

            is TcpConnectProvider.ConnectResult.Success -> {
                try {
                    context.ok()
                } catch (_: Throwable) {
                    context.ioError()
                    return
                }
            }

            TcpConnectProvider.ConnectResult.UnknownError -> {
                context.ioError()
                return
            }

            TcpConnectProvider.ConnectResult.Unreachable -> {
                context.noRouteToHostException()
                return
            }
        }
//        val channel = withTimeoutOrNull(5.seconds) {
//            TcpConnectChannel.connect(
//                host = host,
//                port = port,
//                channel = TODO()
//            )
//        }
//        if (channel == null) {
//            println("HttpProxy:: can't connect to $host:$port")
//            context.notAvailable()
//            return
//        }
//        val (read, write) = try {
//            context.ok()
//        } catch (_: Throwable) {
//            channel.cancel()
//            channel.close()
//            return
//        }
        try {
            pw.binom.utils.connect(
                outcome = channel.second,
                income = channel.first,
                a = result.writeChannel,
                b = result.readChannel,
            )
        } catch (_: Throwable) {
            result.close()
        }
    }
}
