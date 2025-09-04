package pw.binom.transport.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import pw.binom.io.AsyncChannel
import pw.binom.io.AsyncInput
import pw.binom.io.IOException
import pw.binom.io.socket.DomainSocketAddress
import pw.binom.io.use
import pw.binom.io.wrap
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.warn
import pw.binom.network.NetworkManager
import pw.binom.network.tcpConnect
import pw.binom.transport.MultiplexSocket
import pw.binom.transport.Service
import pw.binom.transport.VirtualManager
import pw.binom.transport.io.Cooper
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.seconds

class TcpBridgeService(
    val scope: CoroutineScope = GlobalScope,
    val context: CoroutineContext = EmptyCoroutineContext,
    val networkManager: NetworkManager,
) : Service {
    companion object {
        private val logger = Logger.getLogger("TcpBridgeService")
        const val ID = 7
        const val ERROR: Byte = 1
        const val OK: Byte = 2

        suspend fun connect(manager: VirtualManager, host: String, port: Int): AsyncChannel {
            logger.info("Connect to $host:$port...")
            val socket = manager.connect(ID)
            logger.info("Writing Host...")
            socket.output.writeString(host)
            socket.output.flush()
            logger.info("Writing Port...")
            socket.output.writeInt(port)
            socket.output.flush()
            logger.info("Waiting response...")
            val code = socket.input.readByte()
            logger.info("Response code: $code")
            when (code) {
                ERROR -> {
                    val msg = socket.input.readString()
                    logger.info("Can't connect: $msg")
                    throw IOException("Remote: $msg")
                }

                OK -> {
                    logger.info("Successful connected")
                    return AsyncChannel.create(
                        input = socket.input,
                        output = socket.output,
                        onClose = { socket.close() }
                    )
                }

                else -> throw IllegalStateException("Unknown code $code")
            }
        }
    }

    suspend fun AsyncInput.readString2(): String {
        println("AsyncInput.readString2:: reading size...")
        val size = readInt()
        println("AsyncInput.readString2:: size is $size")
        val bytes = ByteArray(size)
        readFully(bytes)
        return bytes.decodeToString()
    }

    override fun income(socket: MultiplexSocket) {
        scope.launch(context) {
            socket.use { socket ->
                logger.info("Income connection.. Reading Host")
                val host = try {
                    withTimeout(10.seconds) { socket.input.readString2() }
                } catch (e: Throwable) {
                    e.printStackTrace()
                    throw e
                }
                logger.info("Host: $host. Reading port")
                val port = withTimeout(10.seconds) { socket.input.readInt() }
                logger.info("Port: $port")
                val channel = try {
                    logger.info("Try connect to $host:$port")
                    networkManager.tcpConnect(address = DomainSocketAddress(host = host, port = port).resolve())
                } catch (e: Throwable) {
                    logger.warn(text = "Can't connect", exception = e)
                    socket.output.writeByte(ERROR)
                    socket.output.writeString(e.toString())
                    socket.output.flush()
                    return@use
                }
                logger.info("Connected to $host:$port successful. Starting exchange")
                channel.use { channel ->
                    socket.output.writeByte(OK)
                    socket.output.flush()
                    Cooper.exchange(
                        first = AsyncChannel.create(
                            input = socket.input,
                            output = socket.output,
                        ),
                        second = channel
                    ).join()
                }
            }
        }
    }
}
