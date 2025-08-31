package pw.binom.services

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import pw.binom.frame.FrameChannelWithMeta
import pw.binom.io.ByteBuffer

interface VirtualChannelService {
    val income: SendChannel<ByteBuffer>
    val outcome: ReceiveChannel<ByteBuffer>

    suspend fun income(byteBuffer: ByteBuffer)
    suspend fun accept(): FrameChannelWithMeta
    suspend fun createChannel(): FrameChannelWithMeta
}
