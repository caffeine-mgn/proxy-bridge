package pw.binom

@Deprecated(message = "Not use it")
object VirtualChannelManager2
/*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.isActive
import pw.binom.frame.virtual.VirtualChannel
import kotlin.coroutines.coroutineContext

class VirtualChannelManager2 : VirtualChannelManager {
    override val channelCount: Int
        get() = TODO("Not yet implemented")

    private val incomeChannel = Channel<Unit>()

    suspend fun processing() {
        while (coroutineContext.isActive) {
            try {
                incomeChannel.receive()
            } catch (_: ClosedReceiveChannelException) {
                break
            } catch (_: CancellationException) {
                break
            }
        }
    }

    override fun getOrCreateChannel(id: ChannelId): VirtualChannel {
        TODO("Not yet implemented")
    }

    override suspend fun asyncClose() {
        TODO("Not yet implemented")
    }
}
*/
