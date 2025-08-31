package pw.binom.services

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.koin.core.annotation.Single
import pw.binom.*
import pw.binom.frame.FrameChannel
import pw.binom.frame.FrameChannelWithMeta
import pw.binom.frame.FrameInput
import pw.binom.frame.FrameOutput
import pw.binom.frame.FrameReceiver
import pw.binom.frame.FrameReceiverWithMeta
import pw.binom.frame.FrameResult
import pw.binom.frame.PackageSize
import pw.binom.frame.virtual.VirtualChannelManagerImpl3
import pw.binom.io.ByteBuffer
import pw.binom.logger.Logger
import pw.binom.logger.infoSync
import pw.binom.network.NetworkManager
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.inject
import kotlin.time.Duration.Companion.seconds

@Single
class VirtualChannelServiceImpl2(
    val bufferSize: PackageSize,
    val serverMode: Boolean,
    networkManager: Lazy<NetworkManager>,
) : VirtualChannelService {
    private val networkManager: NetworkManager by networkManager
    private val channelIncome = Channel<ByteBuffer>()
    private val channelOutcome = Channel<ByteBuffer>()

    override val income: SendChannel<ByteBuffer>
        get() = channelIncome
    override val outcome: ReceiveChannel<ByteBuffer>
        get() = channelOutcome

    override suspend fun income(byteBuffer: ByteBuffer) {
        channelIncome.send(byteBuffer)
    }

    override suspend fun accept(): FrameChannelWithMeta {
        val channelId = virtualChannelManager.accept()
        val income = virtualChannelManager.listen(channelId = channelId)
        val newChannel =
            VirtualChannel(channelId = channelId, reader = income, virtualChannelManager = virtualChannelManager)
        newChannel.meta["owner"] = "other"
        return newChannel
    }

    private val channel by BeanLifeCycle.afterInit {
        FrameChannelChannel(
            income = channelIncome,
            outcome = channelOutcome,
            bufferSize = bufferSize
        )
    }

    private val virtualChannelManager by BeanLifeCycle.afterInit {
        VirtualChannelManagerImpl3(
            source = channel,
            context = this.networkManager,
            serverMode = serverMode,
        )
    }

    init {
        BeanLifeCycle.postConstruct {
            virtualChannelManager.start()
        }

        BeanLifeCycle.preDestroy {
            virtualChannelManager.asyncClose()
        }
    }

    override suspend fun createChannel(): FrameChannelWithMeta {
        val channelId = virtualChannelManager.new()
        val income = virtualChannelManager.listen(channelId = channelId)
        val newChannel =
            VirtualChannel(channelId = channelId, reader = income, virtualChannelManager = virtualChannelManager)
        newChannel.meta["owner"] = "self"
        return newChannel
    }

    private class VirtualChannel(
        private val channelId: ChannelId,
        private val reader: FrameReceiverWithMeta,
        private val virtualChannelManager: VirtualChannelManagerImpl3
    ) : FrameChannelWithMeta {
        override val meta: MutableMap<String, String> = HashMap()
        override fun toString(): String = "Channel #${channelId.raw}: $meta"
        private val logger by Logger.ofThisOrGlobal
        override val bufferSize: PackageSize
            get() = reader.bufferSize

        override suspend fun <T> readFrame(func: (FrameInput) -> T): FrameResult<T> =
            reader.readFrame(func)

        override suspend fun asyncClose() {
            reader.asyncCloseAnyway()
        }

        override suspend fun <T> sendFrame(func: (FrameOutput) -> T): FrameResult<T> =
            timeoutChecker(timeout = 2.seconds, onTimeout = {
                logger.infoSync("Pushing data to bluetooth timeout! Slow write $reader...")
            }) {
                virtualChannelManager.send(channelId, func)
            }
    }
}
