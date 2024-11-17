package pw.binom.proxy.controllers

import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.strong.inject
import pw.binom.subchannel.commands.BenchmarkCommand
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class BenchmarkHandler : HttpHandler {
    private val benchmarkCommand by inject<BenchmarkCommand>()
    override suspend fun handle(exchange: HttpServerExchange) {
        val time = measureTimedValue {
            benchmarkCommand.new().make(
                time = 5.seconds,
                size = 1024 * 5
            )
        }
        val resp = exchange.response()
        resp.status = 200
        val dataInSeconds = time.value / (time.duration.inWholeMilliseconds * 0.001)
        resp.send("Time: ${time.duration}, Data: ${time.value} bytes. Speed $dataInSeconds bytes/seconds, ${dataInSeconds / 1024} kbytes/seconds, ${dataInSeconds / 1024 / 1024} mbytes/seconds")
    }
}
