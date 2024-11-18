package pw.binom.proxy.controllers

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.network.NetworkManager
import pw.binom.strong.inject
import pw.binom.subchannel.commands.BenchmarkCommand
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class BenchmarkHandler : HttpHandler {
    private val benchmarkCommand by inject<BenchmarkCommand>()
    private val networkManager by inject<NetworkManager>()
    override suspend fun handle(exchange: HttpServerExchange) {
        val works = (0 until 10).map {
            GlobalScope.async(networkManager) {
                measureTimedValue {
                    benchmarkCommand.new().make(
                        time = 10.seconds,
                        size = 1024 * 8
                    )
                }
            }
        }
        val resp = exchange.response()
        resp.status = 200
        val sb = StringBuilder()
        works.awaitAll().forEach { time ->
            val dataInSeconds = time.value / (time.duration.inWholeMilliseconds * 0.001)
            sb.appendLine("Time: ${time.duration}, Data: ${time.value} bytes. Speed $dataInSeconds bytes/seconds, ${dataInSeconds / 1024} kbytes/seconds, ${dataInSeconds / 1024 / 1024} mbytes/seconds")
        }
        resp.send(sb.toString())
    }
}
