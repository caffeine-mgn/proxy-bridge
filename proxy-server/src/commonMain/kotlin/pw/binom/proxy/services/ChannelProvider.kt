package pw.binom.proxy.services

import pw.binom.frame.FrameChannel

/**
 * Выделяет каналы для логического общения.
 * Предполагается что канал вызывается когда нужно, далее происходит общение,
 * а за тем канал сразу же закрывается. То есть оптимизацией переоткрытия канала заниматься не надо.
 * Оптимизацией подключения будет заниматься вот этот вот класс
 */
interface ChannelProvider {
    suspend fun getNewChannel(): FrameChannel
}
