package pw.binom.proxy.node

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.io.AsyncChannel
import pw.binom.io.AsyncInput
import pw.binom.io.AsyncOutput
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.proxy.CompositeChannelManager
import kotlin.coroutines.resume

class ClientService {
    private var existClient: ClientConnection? = null
    private val waters = HashSet<CancellableContinuation<ClientConnection>>()
    private val logger by Logger.ofThisOrGlobal
    private val connectionWater =
        HashMap<Int, CancellableContinuation<Pair<AsyncChannel, CancellableContinuation<Unit>>>>()

    suspend fun webSocketConnected(id: Int, connection: suspend () -> AsyncChannel) {
        compositeChannelManager.useChannel(
            id = id,
            channel = connection()
        )
    }

    suspend fun inputConnected(id: Int, connection: suspend () -> AsyncInput) {
        compositeChannelManager.useInput(
            id = id,
            input = connection()
        )
    }

    suspend fun outputConnected(id: Int, connection: suspend () -> AsyncOutput) {
        compositeChannelManager.useOutput(
            id = id,
            output = connection()
        )
    }

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
    private val compositeChannelManager = CompositeChannelManager<Int>()

    suspend fun putFile(path: String, input: AsyncInput) {
        waitClient().putFile(
            path = path,
            file = input,
        )
    }

    suspend fun connectTo(host: String, port: Int): AsyncChannel {
        val connectionId = channelCounter++
//        val scope = coroutineContext
        val client = waitClient()

        logger.info("Send to client trying to connect $host:$port")
        client.connect(
            host = host,
            port = port,
            channelId = connectionId,
        )
        logger.info("Waiting $connectionId transport channel")
        val c = compositeChannelManager.getChannel(connectionId)
        logger.info("Transport $connectionId channel connected")
        return c
        /*
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
            continuation = l.second,
        )
        */
    }
}
