package pw.binom

import pw.binom.io.AsyncCloseable

interface Behavior : AsyncCloseable {
    /**
     * Запускает поведение
     */
    suspend fun run()
}
