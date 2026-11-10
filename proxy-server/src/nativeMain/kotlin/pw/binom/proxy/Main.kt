package pw.binom.proxy

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Native-точка входа (linux/macos/windows). Общая логика — в
 * [loadConfiguration]/[startProxy], здесь только удержание процесса.
 */
fun main(args: Array<String>) {
    if (Cli.run(args)) return

    val config = loadConfiguration() ?: return
    startProxy(config)

    runBlocking {
        while (true) {
            delay(1_000)
        }
    }
}
