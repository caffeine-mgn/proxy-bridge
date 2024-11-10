package pw.binom.communicate.tcp

import pw.binom.*
import pw.binom.strong.inject

//class TcpDataPair {
//    private val tcpConnectionFactory by inject<TcpConnectionFactory>()
//    suspend fun start(data: TcpData, channel: FrameChannel) {
//        val remote = tcpConnectionFactory.connect(
//            host = data.host,
//            port = data.port.toInt(),
//        )
//        channel.readFrame()
//        channel.sendFrame {
//
//        }
//    }
//}
