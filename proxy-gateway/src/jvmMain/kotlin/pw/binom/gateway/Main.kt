package pw.binom.gateway

import kotlinx.coroutines.runBlocking

object MainJvm {
    @JvmStatic
    @JvmName("main")
    fun mainJvm(args: Array<String>) {
        runBlocking { main(args) }
    }
}
