package pw.binom.communicate.tcp

import kotlinx.coroutines.*
import pw.binom.*
import pw.binom.frame.FrameChannel
import pw.binom.strong.*
import pw.binom.io.socket.UnknownHostException

@Deprecated(message = "Not use it")
class TcpCommunicateClient(
    val channel: FrameChannel,
    val data: TcpClientData,
    val strong: Strong,
) {
    private val tcpConnectionFactory by strong.inject<TcpConnectionFactory>()

    suspend fun processing() {
        try {
            val tcp = tcpConnectionFactory.connect(
                host = data.host,
                port = data.port.toInt(),
            )
        } catch (e: UnknownHostException) {

        }
        while (currentCoroutineContext().isActive) {

        }
    }
}

@Deprecated(message = "Not use it")
object TcpClient {
    const val HOST_NOT_FOUND: Byte = 2
    const val ERROR: Byte = 3

    const val STREAM_CLOSED: Byte = 5
    const val INPUT_CLOSED: Byte = 6

//    suspend fun processingStream(frameChannel: FrameChannel, remoteChannel: AsyncChannel): Byte {
//        val result = Cooper.exchange(
//            stream = remoteChannel,
//            frame = frameChannel,
//        )
//        return when (result) {
//            Cooper.CloseReason.CHANNEL_CLOSED -> STREAM_CLOSED
//            Cooper.CloseReason.FRAME_CLOSED -> INPUT_CLOSED
//        }
//    }

//    suspend fun processingConnect(
//        host: String,
//        port: Int,
//        frameChannel: FrameChannel,
//        tcpConnectionFactory: TcpConnectionFactory,
//    ): Byte {
//        val remoteChannel = try {
//            tcpConnectionFactory.connect(
//                host = host,
//                port = port,
//            )
//        } catch (_: UnknownHostException) {
//            return HOST_NOT_FOUND
//        } catch (_: Throwable) {
//            return ERROR
//        }
//        val result = Cooper.exchange(
//            stream = remoteChannel,
//            frame = frameChannel,
//        )
//        return when (result) {
//            Cooper.CloseReason.CHANNEL_CLOSED -> STREAM_CLOSED
//            Cooper.CloseReason.FRAME_CLOSED -> INPUT_CLOSED
//        }
//    }
}
