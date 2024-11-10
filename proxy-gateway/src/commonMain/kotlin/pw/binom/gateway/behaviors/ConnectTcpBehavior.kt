package pw.binom.gateway.behaviors

import kotlinx.coroutines.Deferred
import pw.binom.*
import pw.binom.compression.zlib.AsyncGZIPInput
import pw.binom.compression.zlib.AsyncGZIPOutput
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.socket.UnknownHostException
import pw.binom.proxy.ProxyClient
import pw.binom.proxy.channels.TransportChannel
import pw.binom.proxy.dto.ControlEventDto
import pw.binom.io.AsyncChannel
import pw.binom.io.ByteBuffer
import pw.binom.io.useAsync
import pw.binom.network.SocketConnectException
/*
class ConnectTcpBehavior private constructor(
    private val from: FrameChannel,
    private val tcpChannel: AsyncChannel,
    private val client: ProxyClient,
    val host: String,
    val port: Int,
) : Behavior {
    companion object {
        suspend fun start(
            client: ProxyClient,
            from: FrameChannel,
            host: String,
            port: Int,
            tcpConnectionFactory: TcpConnectionFactory,
            compressLevel: Int,
        ): ConnectTcpBehavior? {
            val tcpChannel = try {
                tcpConnectionFactory.connect(
                    host = host,
                    port = port,
                )
            } catch (e: UnknownHostException) {
                client.sendEvent(
                    ControlEventDto(
                        proxyError = ControlEventDto.ProxyError(
                            channelId = from.id,
                            msg = "Unknown Host $host"
                        )
                    )
                )
                return null
            } catch (e: SocketConnectException) {
                client.sendEvent(
                    ControlEventDto(
                        proxyError = ControlEventDto.ProxyError(
                            channelId = from.id,
                            msg = e.message
                        )
                    )
                )
                return null
            } catch (e: Throwable) {
                println("Can't connect to $host:$port: $e\n${e.stackTraceToString()}")
                client.sendEvent(
                    ControlEventDto(
                        proxyError = ControlEventDto.ProxyError(
                            channelId = from.id,
                            msg = e.message
                        )
                    )
                )
                return null
            }
            client.sendEvent(
                ControlEventDto(
                    proxyConnected = ControlEventDto.ProxyConnected(
                        channelId = from.id,
                    )
                )
            )
            val channel = if (compressLevel <= 0) {
                tcpChannel
            } else {
                AsyncChannel.create(
                    input = AsyncGZIPInput(stream = tcpChannel, bufferSize = DEFAULT_BUFFER_SIZE),
                    output = AsyncGZIPOutput(
                        stream = tcpChannel,
                        level = compressLevel,
                        bufferSize = DEFAULT_BUFFER_SIZE
                    )
                )
            }
            return ConnectTcpBehavior(
                tcpChannel = channel,
                from = from,
                client = client,
                host = host,
                port = port,
            )
        }
    }

    private val lock = SpinLock()
    private var leftJob: Deferred<StreamBridge.ReasonForStopping>? = null
    private var rightJob: Deferred<StreamBridge.ReasonForStopping>? = null
    private var remoteInterrupted = false
    override val description: String
        get() = "tcp-$host:$port"

    private var stream: ClosableAsyncChannel? = null

    override suspend fun run() {
        lock.lock()
        leftJob = null
        rightJob = null

        val copyResult = ClosableAsyncChannel(stream = from, closeStream = {}).useAsync { left ->
            stream = left
            StreamBridge.sync(
                left = left,
                right = tcpChannel,
                bufferSize = DEFAULT_BUFFER_SIZE,
                rightProvider = { rightJob = it },
                leftProvider = { leftJob = it },
                syncStarted = { lock.unlock() },
                exceptionHappened = { lock.unlock() }
            )
        }
        if (remoteInterrupted) {
            val e = StreamBridge.ChannelBreak("Remote interrupted")
            leftJob?.cancel(e)
            rightJob?.cancel(e)
            tcpChannel.asyncCloseAnyway()
            return
        }
        if (copyResult == StreamBridge.ReasonForStopping.RIGHT) {
            client.sendEvent(ControlEventDto(chanelEof = ControlEventDto.ChanelEof(channelId = from.id)))
            leftJob?.cancel(StreamBridge.ChannelBreak("Tcp socket finished"))
        }
        if (copyResult == StreamBridge.ReasonForStopping.LEFT) {
            rightJob?.cancel(StreamBridge.ChannelBreak("Tcp socket finished"))
            tcpChannel.asyncCloseAnyway()
        }
        println("copyResult=$copyResult")
    }

    override suspend fun asyncClose() {
//        val leftJob: Deferred<StreamBridge.ReasonForStopping>?
//        val rightJob: Deferred<StreamBridge.ReasonForStopping>?
        lock.synchronize {
            remoteInterrupted = true
//            leftJob = this.leftJob
//            rightJob = this.rightJob
//            this.leftJob = null
//            this.rightJob = null
            stream//?.asyncClose()
        }?.asyncClose()
//        val e = StreamBridge.ChannelBreak("Remote interrupted")
//        leftJob?.cancel(e)
//        rightJob?.cancel(e)
    }
}
*/
