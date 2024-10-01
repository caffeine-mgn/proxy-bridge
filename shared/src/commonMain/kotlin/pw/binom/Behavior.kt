package pw.binom

import pw.binom.io.AsyncCloseable

interface Behavior : AsyncCloseable {
    val description: String

    /**
     * Запускает поведение
     */
    suspend fun run()
}
