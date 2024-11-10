package pw.binom.proxy.behaviors
/*
import kotlinx.coroutines.Deferred
import pw.binom.*
import pw.binom.atomic.AtomicLong
import pw.binom.compression.zlib.AsyncGZIPInput
import pw.binom.compression.zlib.AsyncGZIPOutput
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.gateway.GatewayClient
import pw.binom.io.AsyncChannel
import pw.binom.io.useAsync
import pw.binom.proxy.channels.TransportChannel
import pw.binom.proxy.dto.ControlRequestDto

class TcpBridgeBehavior private constructor(
    val channel: TransportChannel,
    val tcpChannel: AsyncChannel,
    val client: GatewayClient,
    val host: String,
    val port: Int,
) : Behavior {
    companion object {
        fun create(
            from: TransportChannel,
            tcp: AsyncChannel,
            client: GatewayClient,
            host: String,
            port: Int,
            compressLevel: Int,
        ): TcpBridgeBehavior =
            TcpBridgeBehavior(
                channel = from,
                tcpChannel = if (compressLevel <= 0) {
                    tcp
                } else {
                    AsyncChannel.create(
                        input = AsyncGZIPInput(stream = tcp, bufferSize = DEFAULT_BUFFER_SIZE),
                        output = AsyncGZIPOutput(stream = tcp, bufferSize = DEFAULT_BUFFER_SIZE),
                    )
                },
                client = client,
                host = host,
                port = port,
            )
    }

    private val lock = SpinLock()
    private var leftJob: Deferred<StreamBridge.ReasonForStopping>? = null
    private var rightJob: Deferred<StreamBridge.ReasonForStopping>? = null
    private var remoteInterrupted = false

    override val description
        get() = "tcp-$host:$port input: ${input.getValue()}, output: ${output.getValue()}"

    private val output = AtomicLong(0)
    private val input = AtomicLong(0)

    override suspend fun run() {
        lock.lock()

        val copyResult =
            ClosableAsyncChannel(stream = channel, closeStream = {}).useAsync { left ->
                StreamBridge.sync(
                    left = left,
                    right = tcpChannel,
                    bufferSize = DEFAULT_BUFFER_SIZE,
                    leftProvider = { leftJob = it },
                    rightProvider = { rightJob = it },
                    exceptionHappened = { lock.unlock() },
                    syncStarted = { lock.unlock() },
                    transferToLeft = {
                        output.addAndGet(it.toLong())
                    },
                    transferToRight = {
                        input.addAndGet(it.toLong())
                    },
                )
            }
        if (remoteInterrupted) {
            val e = StreamBridge.ChannelBreak("Remote interrupted")
            leftJob?.cancel(e)
            rightJob?.cancel(e)
            tcpChannel.asyncCloseAnyway()
            return
        }
        client.sendCmd(ControlRequestDto(resetChannel = ControlRequestDto.ResetChannel(channel.id)))
        if (copyResult == StreamBridge.ReasonForStopping.RIGHT) {
            leftJob?.cancel(StreamBridge.ChannelBreak("Tcp socket finished"))
        }
        if (copyResult == StreamBridge.ReasonForStopping.LEFT) {
            rightJob?.cancel(StreamBridge.ChannelBreak("Tcp socket finished"))
            tcpChannel.asyncCloseAnyway()
        }
    }

    override suspend fun asyncClose() {
        val leftJob: Deferred<StreamBridge.ReasonForStopping>?
        val rightJob: Deferred<StreamBridge.ReasonForStopping>?
        lock.synchronize {
            remoteInterrupted = true
            leftJob = this.leftJob
            rightJob = this.rightJob
            this.leftJob = null
            this.rightJob = null
        }
        val e = StreamBridge.ChannelBreak("Remote interrupted")
        leftJob?.cancel(e)
        rightJob?.cancel(e)
    }
}
*/
