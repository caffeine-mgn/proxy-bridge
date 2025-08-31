package pw.binom.subchannel

import pw.binom.Cooper
import pw.binom.frame.FrameChannel
import pw.binom.frame.FrameChannelWithMeta
import pw.binom.io.AsyncChannel
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logging.Variables

class TcpExchange(
    val host: String,
    val port: Int,
    val channel: FrameChannelWithMeta,
) {
    private val logger by Logger.ofThisOrGlobal
    suspend fun start(stream: AsyncChannel) {
        Variables.with("address" to "$host:$port") {
            logger.info("Start TCP exchange")
            try {
                val result = Cooper.exchange(
                    stream = stream,
                    frame = channel,
                    channelName = "",
                )
                logger.info("Stop TCP exchange: $result")
            } catch (e: Throwable) {
                logger.info("Stop TCP exchange")
            }
        }
    }
}
