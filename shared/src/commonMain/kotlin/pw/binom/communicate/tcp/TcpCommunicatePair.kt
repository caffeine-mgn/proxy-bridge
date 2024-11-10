package pw.binom.communicate.tcp

import kotlinx.serialization.KSerializer
import pw.binom.AutoCloseFrameChannel
import pw.binom.Cooper
import pw.binom.frame.FrameChannel
import pw.binom.TcpConnectionFactory
import pw.binom.communicate.CommunicatePair
import pw.binom.frame.FrameInput
import pw.binom.frame.FrameOutput
import pw.binom.frame.FrameResult
import pw.binom.io.AsyncChannel
import pw.binom.io.ClosedException
import pw.binom.io.socket.UnknownHostException
import pw.binom.io.useAsync
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.infoSync
import pw.binom.strong.inject
import kotlin.random.Random

@Deprecated(message = "Not use it")
class TcpCommunicatePair : CommunicatePair<TcpClientData, AsyncChannel> {
    override val code: Short
        get() = 1
    override val clientSerializer: KSerializer<TcpClientData>
        get() = TcpClientData.serializer()

    private val logger by Logger.ofThisOrGlobal

    private val tcpConnectionFactory by inject<TcpConnectionFactory>()

    companion object {
        const val UNKNOWN_HOST: Byte = 0x1
        const val CONNECT_ERROR: Byte = 0x2
        const val CONNECTED: Byte = 0x3
        const val SERVER_HELLO: Byte = 0x2d
        const val CLIENT_HELLO: Byte = 0x1f

        suspend fun serverHandshake(channel: FrameChannel): AutoCloseFrameChannel {
            val id = Random.nextInt()
            val sendResult = channel.sendFrame {
                it.writeByte(SERVER_HELLO)
                it.writeInt(id)
            }
            if (sendResult.isClosed){
                println("Stream $id. Can't send SERVER-HELLO")
                throw IllegalStateException("")
            }
            val remoteId = channel.readFrame {
                check(it.readByte() == CLIENT_HELLO)
                it.readInt()
            }.getOrThrow()
            check(remoteId == id) { "Invalid remote id. remote: $remoteId, self: $id" }
            val channel = AutoCloseFrameChannel(channel = channel, id = id)

            val cmd = channel.readFrame {
                val cmd = it.readByte()
                when (cmd) {
                    UNKNOWN_HOST -> UnknownHostException()
                    CONNECT_ERROR -> {
                        val data = it.readByteArray(it.readInt())
                        RuntimeException(data.decodeToString())
                    }

                    CONNECTED -> null
                    else -> RuntimeException("Unknown code $cmd")
                }
            }
            if (cmd.isClosed) {
                channel.asyncClose()
                throw ClosedException()
            }
            val error = cmd.getOrThrow()
            if (error != null) {
                channel.asyncClose()
                throw error
            }
            return channel
        }
    }

    override suspend fun startClient(data: TcpClientData, channel: FrameChannel) {
        logger.info("Start client $channel")
        val idResult = channel.readFrame {
            val cmd = it.readByte()
            if (cmd != SERVER_HELLO) {
                logger.infoSync("Illegal start $cmd (0x${cmd.toUByte().toString(16)})")
                throw IllegalStateException("Illegal start $cmd (0x${cmd.toUByte().toString(16)})")
            }
            it.readInt()
        }
        if (idResult.isClosed) {
            logger.info("Stream closed! Can't read SERVER_HELLO 0x${SERVER_HELLO.toUByte().toString(16)}")
            throw IllegalStateException("Illegal stream closed!")
        }
        val id = idResult.getOrThrow()
        logger.info("Remote ID is $id. send self...")
        channel.sendFrame {
            it.writeByte(CLIENT_HELLO.toByte())
            it.writeInt(id)
        }.getOrThrow()
        println("Self ID send id=$id")
        AutoCloseFrameChannel(channel = channel, id = id).useAsync { channel ->
            val stream = try {
                tcpConnectionFactory.connect(
                    host = data.host,
                    port = data.port.toInt(),
                )
            } catch (_: UnknownHostException) {
                logger.infoSync("Can't connect. Host \"${data.host}\" not found")
                channel.sendFrame {
                    it.writeByte(UNKNOWN_HOST)
                }
                return
            } catch (e: Throwable) {
                logger.infoSync("Can't connect. Unknown error: $e")
                channel.sendFrame {
                    it.writeByte(CONNECT_ERROR)
                    val msgBytes = (e.message?.toString() ?: e.toString()).encodeToByteArray()
                    it.writeInt(msgBytes.size)
                    it.writeByteArray(msgBytes)
                }
                return
            }
            logger.info("Connected")
            channel.sendFrame { it.writeByte(CONNECTED) }
            Cooper.exchange(
                stream = stream,
                frame = channel,
            )
            logger.info("Communication finished!")
        }
    }

    override suspend fun startServer(data: AsyncChannel, channel: FrameChannel) {
        logger.info("Start server $channel")

//        val id = Random.nextInt()
//        channel.sendFrame {
//            it.writeByte(SERVER_HELLO.toByte())
//            it.writeInt(id)
//        }.getOrThrow()
//        logger.info("Self ID send! id=$id")
        serverHandshake(channel).useAsync { channel ->
            logger.info("Success connected")
            Cooper.exchange(
                stream = data,
                frame = channel,
            )
            logger.info("Communication finished!")
        }
    }
}
