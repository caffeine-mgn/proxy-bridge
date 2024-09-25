package pw.binom.proxy

import pw.binom.io.AsyncChannel

fun interface BridgeJob {
    /**
     * Начинает выполнять работу моста между [AsyncChannel]
     * @return `true` если копирование прервано по инициативе этого канала.
     * Если копирование прервано по инициативе [other] вернёт `false`
     */
    suspend fun start(): Boolean
}
