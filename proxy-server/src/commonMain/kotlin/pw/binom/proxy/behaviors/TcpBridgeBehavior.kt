package pw.binom.proxy.behaviors

import kotlinx.coroutines.Deferred
import pw.binom.*
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.gateway.GatewayClient
import pw.binom.io.AsyncChannel
import pw.binom.proxy.channels.TransportChannel
import pw.binom.proxy.dto.ControlRequestDto

class TcpBridgeBehavior private constructor(
    val channel: TransportChannel,
    val tcpChannel: AsyncChannel,
    val client: GatewayClient,
) : Behavior {
    companion object {
        fun create(
            from: TransportChannel,
            tcp: AsyncChannel,
            client: GatewayClient,
        ): TcpBridgeBehavior =
            TcpBridgeBehavior(
                channel = from,
                tcpChannel = tcp,
                client = client,
            )
    }

    private val lock = SpinLock()
    private var leftJob: Deferred<StreamBridge.ReasonForStopping>? = null
    private var rightJob: Deferred<StreamBridge.ReasonForStopping>? = null
    private var remoteInterrupted = false

    override suspend fun run() {
        lock.lock()
        val copyResult = StreamBridge.sync(
            left = channel,
            right = tcpChannel,
            bufferSize = DEFAULT_BUFFER_SIZE,
            leftProvider = { leftJob = it },
            rightProvider = { rightJob = it },
            exceptionHappened = { lock.unlock() },
            syncStarted = { lock.unlock() },
        )

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
        println("copyResult=$copyResult")
    }

    override suspend fun asyncClose() {
        val leftJob: Deferred<StreamBridge.ReasonForStopping>?
        val rightJob: Deferred<StreamBridge.ReasonForStopping>?
        println("Lock job...")
        lock.synchronize {
            remoteInterrupted = true
            println("Job locked!")
            leftJob = this.leftJob
            rightJob = this.rightJob
            this.leftJob = null
            this.rightJob = null
        }
        println("Try to cancel jobs... leftJob=$leftJob, rightJob=$rightJob")
        val e = StreamBridge.ChannelBreak("Remote interrupted")
        leftJob?.cancel(e)
        rightJob?.cancel(e)
        println("Job success cancelled!")
    }
}
