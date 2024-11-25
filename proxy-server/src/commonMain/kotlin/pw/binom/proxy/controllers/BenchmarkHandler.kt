package pw.binom.proxy.controllers

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.network.NetworkManager
import pw.binom.strong.inject
import pw.binom.subchannel.commands.BenchmarkCommand
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class BenchmarkHandler : HttpHandler {
    private val benchmarkCommand by inject<BenchmarkCommand>()
    private val networkManager by inject<NetworkManager>()
    private val logger by Logger.ofThisOrGlobal
    override suspend fun handle(exchange: HttpServerExchange) {
        val pingCount = 30
        logger.info("Start ping test...")
        val pingTime = measureTime { benchmarkCommand.new().ping(pingCount) }
        val ping = pingTime.toBigDecimal.divide(pingCount.toBigDecimal(), US_CURRENCY)
        logger.info("Ping $ping ms")
        logger.info("Start upload test...")
        val (uploadSize, uploadTime) = measureTimedValue {
            benchmarkCommand.new().uploadTest(
                time = 10.seconds,
                size = 1024 * 8,
                withResp = false,
            )
        }
        val uploadSpeed =
            uploadSize.toBigDecimal(decimalMode = US_CURRENCY).divide(uploadTime.toBigDecimal, US_CURRENCY)
        logger.info("Upload Speed $uploadSpeed bytes/sec, ${uploadSpeed / 1000} kb/sec, ${uploadSpeed / 1000 / 1000} mb/sec")
        logger.info("Start download test...")
        val (downloadSize, downloadTime) = measureTimedValue {
            benchmarkCommand.new().downloadTest(
                time = 10.seconds,
                size = 1024 * 8,
                withResp = false,
            )
        }

        val downloadSpeed =
            downloadSize.toBigDecimal(decimalMode = US_CURRENCY).divide(downloadTime.toBigDecimal, US_CURRENCY)
        logger.info("Download Speed $downloadSpeed bytes/sec, ${downloadSpeed / 1000} kb/sec, ${downloadSpeed / 1000 / 1000} mb/sec")
        val sb = StringBuilder()
        sb.appendLine("Ping $ping ms")
        sb.appendLine("Upload Speed $uploadSpeed bytes/sec, ${uploadSpeed / 1000} kb/sec, ${uploadSpeed / 1000 / 1000} mb/sec")
        sb.appendLine("Download Speed $downloadSpeed bytes/sec, ${downloadSpeed / 1000} kb/sec, ${downloadSpeed / 1000 / 1000} mb/sec")

        val resp = exchange.response()
        resp.status = 200
        resp.send(sb.toString())
    }

    suspend fun handle1(exchange: HttpServerExchange) {
        val works = (0 until 10).map {
            GlobalScope.async(networkManager) {
                measureTimedValue {
                    benchmarkCommand.new().uploadTest(
                        time = 10.seconds,
                        size = 1024 * 8,
                        withResp = false,
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

private val US_CURRENCY = DecimalMode(30, RoundingMode.ROUND_HALF_AWAY_FROM_ZERO, 5)
val Duration.toBigDecimal: BigDecimal
    get() = inWholeMilliseconds.toBigDecimal(decimalMode = US_CURRENCY)// / 1000.toBigDecimal()
