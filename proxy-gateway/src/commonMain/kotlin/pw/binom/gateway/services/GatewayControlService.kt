package pw.binom.gateway.services
/*
import pw.binom.io.Closeable
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.proxy.ProxyClient
import pw.binom.proxy.dto.ControlEventDto
import pw.binom.proxy.dto.ControlRequestDto
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.EventSystem
import pw.binom.strong.inject

@Deprecated(message = "Not use it")
class GatewayControlService {
    private val channelService by inject<ChannelService>()
    private val proxyClient by inject<ProxyClient>()
    private val logger by Logger.ofThisOrGlobal
    private val eventSystem by inject<EventSystem>()
    private var eventListener: Closeable? = null

    init {
        BeanLifeCycle.afterInit {
            eventListener = eventSystem.listen(ControlRequestDto::class) {
                commandProcessing(it)
            }
        }
        BeanLifeCycle.preDestroy {
            eventListener?.close()
        }
    }

    private suspend fun commandProcessing(cmd: ControlRequestDto) {
        logger.info("Income cmd: $cmd")
        when {
            cmd.emmitChannel != null -> {
                val channelId = cmd.emmitChannel!!.id
                val type = cmd.emmitChannel!!.type
                try {
                    logger.info("Emitting channel $channelId")
                    channelService.createChannel(
                        channelId = channelId,
                        type = type,
                        bufferSize = cmd.emmitChannel!!.bufferSize,
                    )
                    logger.info("Channel was emitted")
                } catch (e: Throwable) {
                    proxyClient.sendEvent(
                        ControlEventDto(
                            channelEmmitError = ControlEventDto.ChannelEmmitError(
                                channelId = channelId,
                                msg = e.message ?: "Can't open channel"
                            )
                        )
                    )
                }
            }

            cmd.closeChannel != null -> {
                channelService.close(cmd.closeChannel!!.id)
            }

            cmd.proxyConnect != null -> {
                try {
                    channelService.connect(
                        channelId = cmd.proxyConnect!!.id,
                        host = cmd.proxyConnect!!.host,
                        port = cmd.proxyConnect!!.port,
                        compressLevel = cmd.proxyConnect!!.compressLevel,
                    )
                } catch (e: Throwable) {
                    proxyClient.sendEvent(
                        ControlEventDto(
                            proxyError = ControlEventDto.ProxyError(
                                channelId = cmd.proxyConnect!!.id,
                                msg = e.message ?: e.toString()
                            )
                        )
                    )
                }
            }

//            cmd.resetChannel != null -> {
//                val channelId = cmd.resetChannel!!.id
//                channelService.reset(channelId = channelId)
//            }
        }
    }
}
*/
