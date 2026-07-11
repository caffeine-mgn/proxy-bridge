package pw.binom.multiplexer

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Buffer
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class MultiplexerImpl(
    val input: ReceiveChannel<Buffer>,
    val output: SendChannel<Buffer>,
    idOdd: Boolean,
    ioCoroutineScope: CoroutineScope
) : Multiplexer {
    constructor(
        channel: DuplexChannel,
        idOdd: Boolean,
        ioCoroutineScope: CoroutineScope
    ) : this(
        input = channel.income,
        output = channel.outcome,
        idOdd = idOdd,
        ioCoroutineScope = ioCoroutineScope,
    )

    private val scope = CoroutineScope(ioCoroutineScope.coroutineContext + Job())
    private val idGenerator = AtomicLong(if (idOdd) 1L else 0L)
    private val activeChannelsMutex = Mutex()
    private val activeChannels = HashMap<Int, VirtualChannel>()
    private val pendingChannelsMutex = Mutex()
    private val pendingChannels = HashMap<Int, CompletableDeferred<Unit>>()
    private val incomeChannels = Channel<Int>(Channel.UNLIMITED)
    private val pendingDataLock = Mutex()
    private val pendingData = HashMap<Int, MutableList<Buffer>>()
    private val logger = KotlinLogging.logger {}

    private suspend fun drainPendingData(channelId: Int, target: VirtualChannel) {
        pendingDataLock.withLock {
            val buffered = pendingData.remove(channelId)
            if (buffered != null) {
                for (buf in buffered) {
                    target.income.send(buf)
                }
            }
        }
    }

    override suspend fun accept(): DuplexChannel {
        val incomeChannelId = incomeChannels.receive()
        val channelJob = VirtualChannel(
            id = incomeChannelId,
        )
        activeChannelsMutex.withLock {
            activeChannels[incomeChannelId] = channelJob
        }
        drainPendingData(incomeChannelId, channelJob)
        MultiplexerProtocol.sendResponseNewChannel(channelId = incomeChannelId, physical = output)
        return channelJob
    }

    private inner class VirtualChannel(
        private val id: Int,
    ) : DuplexChannel, AutoCloseable {
        override val income = Channel<Buffer>(Channel.UNLIMITED)
        override val outcome = Channel<Buffer>(Channel.UNLIMITED)
        private val job = scope.launch(CoroutineName("Output channel $id")) {
            try {
                MultiplexerProtocol.copyingLogicalToPhysical(
                    channelId = id,
                    logical = outcome,
                    physical = output,
                )
            } catch (e: CancellationException) {
                logger.debug { "Channel $id closed normally" }
            } catch (e: Throwable){
                logger.error(e) { "Error on channel $id copy finished!" }
            } finally {
                try {
                    MultiplexerProtocol.sendCloseChannel(channelId = id, physical = output)
                } catch (_: Throwable) {
                    // best-effort — close notification may fail if output is already closed
                }
                val e = CancellationException("Closed by outcome channel closed")
                outcome.close(e)
                income.cancel(e)
                activeChannelsMutex.withLock {
                    activeChannels.remove(id)
                }
            }
        }

        init {
            income.invokeOnClose {
                job.cancel()
            }
        }

        override fun close() {
            outcome.close(CancellationException("Channel closed"))
        }
    }


    override suspend fun createChannel(): DuplexChannel {
        val newChannelId = idGenerator.addAndFetch(2L).toInt()

        val deferred = CompletableDeferred<Unit>()
        pendingChannelsMutex.withLock {
            pendingChannels[newChannelId] = deferred
        }

        try {
            MultiplexerProtocol.sendRequestNewChannel(
                channelId = newChannelId,
                physical = output,
            )
        } catch (e: Throwable) {
            pendingChannelsMutex.withLock {
                pendingChannels.remove(newChannelId)
            }
            deferred.completeExceptionally(e)
            throw e
        }

        try {
            deferred.await()
        } catch (e: CancellationException) {
            pendingChannelsMutex.withLock {
                pendingChannels.remove(newChannelId)
            }
            throw e
        }

        val channelJob = VirtualChannel(id = newChannelId)
        activeChannelsMutex.withLock {
            activeChannels[newChannelId] = channelJob
        }
        drainPendingData(newChannelId, channelJob)
        return channelJob
    }

    private val readJob = scope.launch {
        try {
            supervisorScope {
                MultiplexerProtocol.reading(
                    physical = input,
                    handlerOnData = { channelId, data ->
                        val channel = activeChannelsMutex.withLock { activeChannels[channelId] }
                        if (channel == null) {
                            // Канал ещё не зарегистрирован — буферизуем
                            pendingDataLock.withLock {
                                pendingData.getOrPut(channelId) { mutableListOf() }.add(data)
                            }
                        } else {
                            try {
                                channel.income.send(data)
                            } catch (e: CancellationException) {
                                //ignore
                            }
                        }
                    },
                    channelClosed = { channelId ->
                        logger.info { "Income message for close channel $channelId" }
                        val toClose = activeChannelsMutex.withLock {
                            activeChannels.remove(channelId)
                        }
                        toClose?.close()
                    },
                    requestChannel = { channelId ->
                        incomeChannels.send(channelId)
                    },
                    newChannelAccepted = { channelId ->
                        val waiter = pendingChannelsMutex.withLock { pendingChannels.remove(channelId) }
                        if (waiter == null) {
                            MultiplexerProtocol.sendCloseChannel(channelId = channelId, physical = output)
                        } else {
                            waiter.complete(Unit)
                        }
                    },
                )
            }
        } catch (e: CancellationException) {
            // Normal shutdown — close() handles full cleanup
            throw e
        } catch (e: Throwable) {
            logger.error(e) { "readJob crashed — cleaning up multiplexer" }
            // Close all active channels to signal failure to users
            val channelsToClose = activeChannelsMutex.withLock {
                activeChannels.values.toList().also { activeChannels.clear() }
            }
            channelsToClose.forEach {
                it.cancel()          // closes income immediately
                it.close()           // starts graceful shutdown of outcome
            }
            kotlinx.coroutines.runBlocking {
                pendingChannelsMutex.withLock {
                    pendingChannels.values.forEach { it.cancel() }
                    pendingChannels.clear()
                }
            }
            pendingDataLock.withLock {
                pendingData.clear()
            }
            incomeChannels.cancel()
            throw e
        }
    }

    override fun close() {
        try {
            scope.cancel()
            readJob.cancel()
            kotlinx.coroutines.runBlocking {
                val channelsToClose = activeChannelsMutex.withLock {
                    activeChannels.values.toList().also { activeChannels.clear() }
                }
                channelsToClose.forEach { it.close() }
                pendingChannelsMutex.withLock {
                    pendingChannels.values.forEach { it.cancel() }
                    pendingChannels.clear()
                }
                pendingDataLock.withLock {
                    pendingData.clear()
                }
            }
        } catch (e: Throwable) {
            logger.error(e) { "Error during multiplexer close" }
        }
        incomeChannels.cancel()
    }
}
