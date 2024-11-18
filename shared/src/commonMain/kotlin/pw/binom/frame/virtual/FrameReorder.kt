package pw.binom.frame.virtual

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.SendChannel
import pw.binom.*
import pw.binom.coroutines.SimpleAsyncLock2
import pw.binom.io.ByteBuffer

class FrameReorder(
    val channel: SendChannel<ByteBuffer>,
) : AsyncReCloseable() {
    /**
     * Ожидаемый номер следующего фрейма
     */
    private var nextFrame = FrameId.INIT

    /**
     * Пакеты из будущего
     */
    private val notInTimePackages = HashMap<Byte, ByteBuffer>()
    private val incomeLock = SimpleAsyncLock2()

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun income(frame: FrameId, data: ByteBuffer) {
        if (channel.isClosedForSend) {
            throw ClosedSendChannelException(null)
        }
        incomeLock.synchronize {
            if (nextFrame != frame) {
                notInTimePackages[frame.raw] = data
                return@synchronize
            }

            channel.send(data)
            nextFrame = frame.next
            if (notInTimePackages.isEmpty()) {
                return@synchronize
            }
            do {
                val next = nextFrame.next
                val exist = notInTimePackages.remove(next.raw) ?: break
                nextFrame = next
                channel.send(exist)
            } while (true)
        }
    }

    override suspend fun realAsyncClose() {
        incomeLock.synchronize {
            notInTimePackages.values.forEach {
                it.close()
            }
            notInTimePackages.clear()
        }
    }
}
