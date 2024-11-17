package pw.binom.proxy.server
/*

import kotlinx.coroutines.*
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.date.DateTime
import pw.binom.io.AsyncChannel
import pw.binom.io.AsyncInput
import pw.binom.io.AsyncOutput
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.proxy.CompositeChannelManager
import pw.binom.proxy.ControlClient
import pw.binom.extract
import pw.binom.proxy.exceptions.ClientMissingException
import pw.binom.proxy.properties.ProxyProperties
import pw.binom.strong.Strong
import pw.binom.strong.inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Deprecated(message = "Not use it")
class ClientService : Strong.DestroyableBean, Strong.LinkingBean {
    private var existClient: ControlClient? = null
    private val waters = HashSet<Water>()
    private val waterLock = SpinLock()
    private val properties by inject<ProxyProperties>()
    private val logger by Logger.ofThisOrGlobal
    private val connectionWater =
        HashMap<Int, CancellableContinuation<Pair<AsyncChannel, CancellableContinuation<Unit>>>>()

    private class Water(
        val water: CancellableContinuation<ControlClient>,
        val created: DateTime,
    )

    suspend fun webSocketConnected(
        id: Int,
        connection: suspend () -> AsyncChannel,
    ) {
        compositeChannelManager.useChannel(
            id = id,
            channel = connection()
        )
    }

    suspend fun inputConnected(
        id: Int,
        connection: suspend () -> AsyncInput,
    ) {
        compositeChannelManager.useInput(
            id = id,
            input = connection()
        )
    }

    suspend fun outputConnected(
        id: Int,
        connection: suspend () -> AsyncOutput,
    ) {
        compositeChannelManager.useOutput(
            id = id,
            output = connection()
        )
    }

    suspend fun transportProcessing(
        id: Int,
        connection: suspend () -> AsyncChannel,
    ) {
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

    fun clientDisconnected(connection: ControlClient): Boolean {
        val existClient = existClient
        if (existClient != connection) {
            return false
        }
        this.existClient = null
        return true
    }

    fun clientConnected(connection: ControlClient): Boolean {
        val existClient = existClient
        return if (existClient != null) {
            false
        } else {
            this.existClient = connection
            val waters =
                waterLock.synchronize {
                    val h = HashSet(waters)
                    this.waters.clear()
                    h
                }

            waters.forEach {
                it.water.resume(connection)
            }
            true
        }
    }

    private suspend fun waitClient(): ControlClient {
        val existClient = existClient
        if (existClient != null) {
            return existClient
        }
        return suspendCancellableCoroutine {
            val water = Water(water = it, created = DateTime.now)
            it.invokeOnCancellation { _ ->
                waterLock.synchronize {
                    waters -= water
                }
            }
            waterLock.synchronize {
                waters += water
            }
        }
    }

    private var channelCounter = 0
    private val compositeChannelManager = CompositeChannelManager<Int>()

//    suspend fun putFile(path: String, input: AsyncInput) {
//        waitClient().putFile(
//            path = path,
//            file = input,
//        )
//    }

    suspend fun connectTo(
        host: String,
        port: Int,
    ): Pair<Int, AsyncChannel> {
        val connectionId = channelCounter++
//        val scope = coroutineContext
        logger.info("Wait a client...")
        val client = waitClient()

        logger.info("Send to client trying to connect $host:$port")
        client.connect(
            host = host,
            port = port,
            channelId = connectionId
        )
        logger.info("Waiting $connectionId transport channel")
        val channel = compositeChannelManager.getChannel(connectionId)
        logger.info("Transport $connectionId channel connected")
        return connectionId to channel
        */
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
         *//*

    }

    private var cleanupJob: Job? = null

    override suspend fun link(strong: Strong) {
        cleanupJob =
            GlobalScope.launch {
                while (isActive) {
                    try {
                        delay(properties.remoteClientAwaitTimeout)
                        val exp = DateTime.now
                        val exparedWaters =
                            waterLock.synchronize {
                                waters.extract { exp - it.created > properties.remoteClientAwaitTimeout }
                            }
                        exparedWaters.forEach {
                            it.water.resumeWithException(ClientMissingException())
                        }
                    } catch (e: CancellationException) {
                        break
                    }
                }
            }
    }

    override suspend fun destroy(strong: Strong) {
        cleanupJob?.cancelAndJoin()
    }
}
*/
