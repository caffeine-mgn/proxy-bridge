package pw.binom.services

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import pw.binom.ByteBufferPool
import pw.binom.ChannelId
import pw.binom.DEFAULT_BUFFER_SIZE
import pw.binom.frame.PackageSize
import pw.binom.TcpConnectionFactory
import pw.binom.frame.FrameChannel
import pw.binom.frame.virtual.VirtualChannel
import pw.binom.frame.virtual.VirtualChannelManagerImpl2
import pw.binom.io.ByteBuffer
import pw.binom.io.FileSystem
import pw.binom.io.PooledByteBuffer
import pw.binom.io.useAsync
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.infoSync
import pw.binom.logger.severe
import pw.binom.metric.AsyncMetricVisitor
import pw.binom.metric.Metric
import pw.binom.metric.MetricVisitor
import pw.binom.network.NetworkManager
import pw.binom.pool.GenericObjectPool
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.ServiceProvider
import pw.binom.strong.inject
import pw.binom.strong.injectServiceList
import pw.binom.subchannel.Command
import pw.binom.subchannel.WorkerChanelClient
import pw.binom.subchannel.WorkerChannelServer

class VirtualChannelService(val bufferSize: PackageSize) : Metric {

    private lateinit var pool: GenericObjectPool<PooledByteBuffer>
    private lateinit var manager: VirtualChannelManagerImpl2
//    private val tcpConnectionFactory by inject<TcpConnectionFactory>()
    private val networkManager by inject<NetworkManager>()
//    private val fileSystem by inject<FileSystem>(name = "LOCAL_FS")
    private var processingJob: Job? = null
    val income: SendChannel<ByteBuffer>
        get() = manager.income
    val outcome: ReceiveChannel<ByteBuffer>
        get() = manager.outcome
    private val logger by Logger.ofThisOrGlobal
    private var counter = 0.toShort()
    private val commands by injectServiceList<Command<Any?>>()
    private val commandsByCmd = HashMap<Byte, Command<Any?>>()

    fun getSystemChannel() = manager.getOrCreate(ChannelId(0))
    fun createChannel(): FrameChannel {
        val id = counter++
        return manager.getOrCreate(ChannelId(id))
//        while (true) {
//            val id = Random.nextInt().toShort()
//            if (id == 0.toShort()) {
//                continue
//            }
//            return manager.getOrCreate(ChannelId(id))
//        }
    }

    override fun accept(visitor: MetricVisitor) {
        manager.accept(visitor)
    }

    override suspend fun accept(visitor: AsyncMetricVisitor) {
        manager.accept(visitor)
    }

    init {
        BeanLifeCycle.afterInit {
            commandsByCmd.putAll(commands.associateBy { it.cmd })
            pool = ByteBufferPool(size = DEFAULT_BUFFER_SIZE)
            manager = VirtualChannelManagerImpl2(
                bufferSize = bufferSize,
                channelEmitted = { channel ->
                    logger.infoSync("Emmit new channel ${channel.id}")
                    GlobalScope.launch(networkManager) {
                        val cmd = channel.readFrame { it.readByte() }.valueOrNull
                        if (cmd == null) {
                            channel.asyncClose()
                        } else {
                            val command = commandsByCmd[cmd]
                            if (command == null) {
                                channel.asyncClose()
                            } else {
                                command.startClient(channel)
                            }
                        }
//                        channel.useAsync { channel ->
//                            WorkerChannelServer.start(
//                                channel = channel,
//                                tcpConnectionFactory = ServiceProvider.provide(tcpConnectionFactory),
//                                fileSystem = ServiceProvider.provide(fileSystem),
//                            )
//                        }
                    }
                },
                bytePool = pool
            )
            processingJob = GlobalScope.launch(networkManager) {
                logger.info("Start Virtual Channel processing....")
                try {
                    manager.processing()
                } catch (e: Throwable) {
                    logger.severe(text = "Virtual Channel Manager processing closed with error", exception = e)
                } finally {
                    logger.severe("Virtual Channel Manager was stopped!")
                }
            }
            logger.info("Started!")
        }
        BeanLifeCycle.preDestroy {
            processingJob?.cancelAndJoin()
            pool.close()
        }
    }
}
