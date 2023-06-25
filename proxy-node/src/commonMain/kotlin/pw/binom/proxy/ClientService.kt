package pw.binom.proxy

import kotlinx.coroutines.*
import pw.binom.io.AsyncChannel
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.infoSync
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

class ClientService {
    private var existClient: ClientConnection? = null
    private val waters = HashSet<CancellableContinuation<ClientConnection>>()
    private val logger by Logger.ofThisOrGlobal
    private val connectionWater =
        HashMap<Int, CancellableContinuation<Pair<AsyncChannel, CancellableContinuation<Unit>>>>()

    suspend fun transportProcessing(id: Int, connection: suspend () -> AsyncChannel) {
        val water = connectionWater.remove(id)
        if (water == null) {
            logger.info("Water for id=$id not found!")
            return
        }
        logger.info("Accepting...")
        val ws = connection()
        logger.info("Accepted!")
        suspendCancellableCoroutine { con ->
            water.resume(ws to con)
        }
        logger.info("Finished!")
    }

    fun clientDisconnected(connection: ClientConnection): Boolean {
        val existClient = existClient
        if (existClient != connection) {
            return false
        }
        this.existClient = null
        return true
    }

    fun clientConnected(connection: ClientConnection): Boolean {
        val existClient = existClient
        return if (existClient != null) {
            false
        } else {
            this.existClient = connection
            val waters = HashSet(waters)
            this.waters.clear()
            waters.forEach {
                it.resume(connection)
            }
            true
        }
    }

    private suspend fun waitClient(): ClientConnection {
        val existClient = existClient
        if (existClient != null) {
            return existClient
        }
        return suspendCancellableCoroutine {
            it.invokeOnCancellation { _ ->
                waters -= it
            }
            waters += it
        }
    }

    private var channelCounter = 0

    suspend fun connectTo(host: String, port: Int): RemoteAsyncChannel {
        logger.info("Send to client trying to connect $host:$port")
        val connectionId = channelCounter++
        val scope = coroutineContext
        val client = waitClient()

        val l = suspendCancellableCoroutine {
            logger.infoSync("wait connection with id=$connectionId")
            it.invokeOnCancellation {
                connectionWater.remove(connectionId)
            }
            connectionWater[connectionId] = it
            logger.infoSync("Sending connect offer")

            GlobalScope.launch(scope) {
                logger.info("Sending offer to connect connectionId: $connectionId")
                client.connect(
                    host = host,
                    port = port,
                    channelId = connectionId,
                )
            }
        }
        logger.info("Transport connected!!!")
        return RemoteAsyncChannel(
            channel = l.first,
            continuation = l.second
        )
    }
}