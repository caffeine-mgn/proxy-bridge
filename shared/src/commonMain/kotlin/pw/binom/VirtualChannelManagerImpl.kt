package pw.binom

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pw.binom.atomic.AtomicBoolean
import pw.binom.frame.FrameChannel
import pw.binom.frame.FrameOutput
import pw.binom.frame.FrameResult
import pw.binom.frame.PackageSize
import pw.binom.frame.virtual.VirtualChannel
import pw.binom.io.FileSystem
import pw.binom.io.useAsync
import pw.binom.logger.info
import pw.binom.logger.infoSync
import pw.binom.metric.MetricProvider
import pw.binom.metric.MetricProviderImpl
import pw.binom.metric.MetricUnit
import pw.binom.strong.ServiceProvider
import pw.binom.subchannel.WorkerChannelServer
import kotlin.coroutines.coroutineContext
@Deprecated(message = "Not use it")
class VirtualChannelManagerImpl(
    val other: FrameChannel,
    val tcpConnectionFactory: ServiceProvider<TcpConnectionFactory>,
    val fileSystem: ServiceProvider<FileSystem>,
) : AbstractVirtualChannelManager(),MetricProvider {

    private val metricProvider = MetricProviderImpl()
    override val metrics: List<MetricUnit> by metricProvider
    private val virtualChannelCountMetric = metricProvider.gaugeLong("virtual_channel_total"){
        channelCount.toLong()
    }

    private val processing = AtomicBoolean(false)
    override val bufferSize: PackageSize
        get() = other.bufferSize

    suspend fun processing() {
        logger.info("Start processing")
        check(processing.compareAndSet(false, true)) { "Processing already ran" }
        try {
            while (coroutineContext.isActive) {
                val r = other.readFrame { input ->
                    income(input)
                }
                if (r.isClosed) {
                    asyncClose()
                    break
                }
            }
        } finally {
            logger.info("Stop processing")
            processing.setValue(false)
        }
    }

    override suspend fun <T> pushFrame(func: (FrameOutput) -> T): FrameResult<T> =
        other.sendFrame(func)

    override fun emittedNewChannel(id: ChannelId, channel: VirtualChannel) {
        logger.infoSync("Channel $id was emitted and started!")
        GlobalScope.launch {
            channel.useAsync { channel ->
                WorkerChannelServer.start(
                    channel = channel,
                    tcpConnectionFactory = tcpConnectionFactory,
                    fileSystem = fileSystem,
                )
            }
        }
    }

    override suspend fun asyncClose() {
        try {
            super.asyncClose()
        } finally {
            other.asyncClose()
        }
    }
}
