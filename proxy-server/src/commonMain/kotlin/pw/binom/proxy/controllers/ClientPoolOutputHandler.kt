package pw.binom.proxy.controllers
/*

import pw.binom.io.http.Encoding
import pw.binom.io.http.Headers
import pw.binom.io.http.headersOf
import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.Urls
import pw.binom.proxy.server.ClientService
import pw.binom.strong.inject

@Deprecated(message = "Not use it")
class ClientPoolOutputHandler : HttpHandler {

    private val clientService by inject<ClientService>()

//    private val waters = HashMap<Int, CancellableContinuation<Pair<AsyncInput, CancellableContinuation<Unit>>>>()
    private val logger by Logger.ofThisOrGlobal

//    suspend fun inputReady(id: Int, input: AsyncInput): Boolean {
//        val water = waters.remove(id) ?: return false
//        suspendCancellableCoroutine { continuation ->
//            water.resume(input to continuation)
//        }
//        return true
//    }

    override suspend fun handle(exchange: HttpServerExchange) {
        logger.info("Request for node write...")
        val id = exchange.requestURI.path.getVariable("id", Urls.TRANSPORT_LONG_POOLING_NODE_WRITE)
            ?.toInt()
        if (id == null) {
            logger.info("Invalid Id")
            throw IllegalArgumentException("invalid id")
        }
        logger.info("Start response... wait client...")
        clientService.outputConnected(
            id = id,
            connection = {
                exchange.startResponse(200, headersOf(Headers.TRANSFER_ENCODING to Encoding.CHUNKED))
                exchange.output
            }
        )
        */
/*
        val input = withTimeoutOrNull(10.seconds) {
            suspendCancellableCoroutine<Pair<AsyncInput, CancellableContinuation<Unit>>> {
                waters[id] = it
            }
        } ?: return
        logger.info("Read channel connected!")
        clientService.transportProcessing(id = id) {
            AsyncChannel.create(
                input = input.first,
                output = exchange.output,
            ) {
                input.second.resume(Unit)
            }
        }
        *//*

    }
}
*/
