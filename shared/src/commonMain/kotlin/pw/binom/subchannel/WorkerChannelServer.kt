package pw.binom.subchannel

import kotlinx.coroutines.withTimeoutOrNull
import pw.binom.Cooper
import pw.binom.frame.FrameChannel
import pw.binom.TcpConnectionFactory
import pw.binom.io.FileSystem
import pw.binom.io.socket.UnknownHostException
import pw.binom.io.useAsync
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.infoSync
import pw.binom.strong.ServiceProvider
import pw.binom.subchannel.WorkerChanelClient.Companion.CONNECTED
import kotlin.time.Duration.Companion.seconds

object WorkerChannelServer {
    private val logger by Logger.ofThisOrGlobal

    private sealed interface Income {
        data class Tcp(
            val host: String,
            val port: Int,
        ) : Income

        data object FSAccess : Income
        data object SpeedTest : Income

        data object NotSupported : Income
    }

    suspend fun start(
        channel: FrameChannel,
        tcpConnectionFactory: ServiceProvider<TcpConnectionFactory>,
        fileSystem: ServiceProvider<FileSystem>,
    ) {
        logger.infoSync("Starting command server")
        val cmd = withTimeoutOrNull(10.seconds) {
            channel.readFrame {
                val cmd = it.readByte()
                when (cmd) {
                    WorkerChanelClient.START_TCP -> Income.Tcp(
                        host = it.readString(),
                        port = it.readInt(),
                    )

                    WorkerChanelClient.SPEED_TEST -> Income.SpeedTest
                    WorkerChanelClient.FS_ACCESS -> Income.FSAccess

                    else -> {
                        logger.infoSync("Unsupported command 0x${cmd.toUByte().toString(16)}")
                        Income.NotSupported
                    }
                }
            }.valueOrNull ?: return@withTimeoutOrNull null
        }
        if (cmd == null) {
            logger.info("Не поступили данные. Прекращаем обработку")
            return
        }
        logger.info("Прочитана команда $cmd")
        when (cmd) {
            is Income.NotSupported -> {
                logger.info("Не поддерживаемая команда. Прекращаем обработку")
                return
//                channel.sendFrame {
//                    it.writeByte(WorkerChanelClient.NOT_SUPPORTED)
//                }
            }

            is Income.FSAccess -> fsAccess(channel = channel, fileSystem = fileSystem)
            is Income.SpeedTest -> SpeedTestServer.processing(channel = channel)
            is Income.Tcp -> {
                tcp(
                    channel = channel,
                    cmd = cmd,
                    tcpConnectionFactory = tcpConnectionFactory,
                )
            }
        }
    }

    private suspend fun fsAccess(channel: FrameChannel, fileSystem: ServiceProvider<FileSystem>) {
        channel.sendFrame { it.writeByte(CONNECTED) }
        FSServer.processing(
            channel = channel,
            fileSystem = fileSystem.value
        )
    }

    private suspend fun tcp(
        channel: FrameChannel,
        cmd: Income.Tcp,
        tcpConnectionFactory: ServiceProvider<TcpConnectionFactory>
    ) {
        val tcpConnectionFactory by tcpConnectionFactory
        val stream = try {
            tcpConnectionFactory.connect(
                host = cmd.host,
                port = cmd.port,
            )
        } catch (_: UnknownHostException) {
            logger.infoSync("Can't connect. Host \"${cmd.host}\" not found")
            channel.sendFrame {
                it.writeByte(WorkerChanelClient.HOST_NOT_FOUND)
            }
            return
        } catch (e: Throwable) {
            logger.infoSync("Can't connect. Unknown error: $e")
            channel.sendFrame {
                it.writeByte(WorkerChanelClient.UNKNOWN_ERROR)
                it.writeString(e.message ?: e.toString())
            }
            return
        }
        channel.sendFrame {
            it.writeByte(CONNECTED)
        }
        stream.useAsync { stream ->
            Cooper.exchange(
                stream = stream,
                frame = channel,
            )
        }
    }
}
