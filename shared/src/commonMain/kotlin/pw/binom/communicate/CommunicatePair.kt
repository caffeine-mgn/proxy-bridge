package pw.binom.communicate

import kotlinx.serialization.KSerializer
import pw.binom.frame.FrameChannel

@Deprecated(message = "Not use it")
interface CommunicatePair<CLIENT_DATA : Any, SERVER_DATA> {
    val code: Short
    val clientSerializer: KSerializer<CLIENT_DATA>
//    fun createClient(data: CLIENT_DATA, frameWriter: FrameWriter): CommunicateClient
//    fun createServer(data: SERVER_DATA, frameWriter: FrameWriter): CommunicateServer

    suspend fun startClient(data: CLIENT_DATA, channel: FrameChannel)
    suspend fun startServer(data: SERVER_DATA, channel: FrameChannel)
}
