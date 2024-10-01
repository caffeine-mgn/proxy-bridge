package pw.binom.gateway.behaviors

import kotlinx.coroutines.Deferred
import pw.binom.*
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.socket.UnknownHostException
import pw.binom.proxy.ProxyClient
import pw.binom.proxy.channels.TransportChannel
import pw.binom.proxy.dto.ControlEventDto
import pw.binom.gateway.services.TcpConnectionFactory
import pw.binom.io.AsyncChannel
import pw.binom.network.SocketConnectException

class ConnectTcpBehavior private constructor(
    private val from: TransportChannel,
    private val tcpChannel: AsyncChannel,
    private val client: ProxyClient,
    val host: String,
    val port: Int,
) : Behavior {
    companion object {
        suspend fun start(
            client: ProxyClient,
            from: TransportChannel,
            host: String,
            port: Int,
            tcpConnectionFactory: TcpConnectionFactory,
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
            return ConnectTcpBehavior(
                tcpChannel = tcpChannel,
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


    override suspend fun run() {
        lock.lock()
        leftJob = null
        rightJob = null
        val copyResult = StreamBridge.sync(
            left = from,
            right = tcpChannel,
            bufferSize = DEFAULT_BUFFER_SIZE,
            rightProvider = { rightJob = it },
            leftProvider = { leftJob = it },
            syncStarted = { lock.unlock() },
            exceptionHappened = { lock.unlock() }
        )
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
