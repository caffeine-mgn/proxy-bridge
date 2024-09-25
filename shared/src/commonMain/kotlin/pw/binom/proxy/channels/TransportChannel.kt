package pw.binom.proxy.channels

import pw.binom.io.AsyncChannel
import pw.binom.proxy.BridgeJob
import pw.binom.proxy.ChannelId

sealed interface TransportChannel : AsyncChannel {
    val id: ChannelId


    fun breakCurrentRole()

    /**
     * Выполняет копирование из текущего канала в [other]
     * @return `true` если копирование прервано по инициативе этого канала.
     * Если копирование прервано по инициативе [other] вернёт `false`
     */
    suspend fun connectWith(
        other: AsyncChannel,
        bufferSize: Int,
    ): BridgeJob
}
