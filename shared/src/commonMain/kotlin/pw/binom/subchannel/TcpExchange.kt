package pw.binom.subchannel

import pw.binom.Cooper
import pw.binom.frame.FrameChannel
import pw.binom.io.AsyncChannel
import pw.binom.logger.Logger
import pw.binom.logger.info

class TcpExchange(val channel: FrameChannel) {
    private val logger by Logger.ofThisOrGlobal
    suspend fun start(stream: AsyncChannel) {
        logger.info("Start TCP exchange")
        try {
            val result = Cooper.exchange(
                stream = stream,
                frame = channel,
            )
            logger.info("Stop TCP exchange: $result")
        } catch (e: Throwable) {
            logger.info("Stop TCP exchange")
        }
    }
}
