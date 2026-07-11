package pw.binom.multiplexer

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Buffer
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resume

@OptIn(ExperimentalAtomicApi::class)
class MultiplexerImpl(
    val input: ReceiveChannel<Buffer>,
    val output: SendChannel<Buffer>,
    idOdd: Boolean,
    private val ioCoroutineScope: CoroutineScope
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

    private val idGenerator = AtomicInt(if (idOdd) 1 else 0)
    private val activeChannelsMutex = Mutex()
    private val activeChannels = HashMap<Int, VirtualChannel>()
    private val pendingChannelsMutex = Mutex()
    private val pendingChannels = HashMap<Int, CancellableContinuation<Unit>>()
    private val incomeChannels = Channel<Int>(Channel.UNLIMITED)
    private val pendingDataLock = AtomicBoolean(false)
    private val pendingData = HashMap<Int, MutableList<Buffer>>()
    private val logger = KotlinLogging.logger {}

    private fun drainPendingData(channelId: Int, target: VirtualChannel) {
        pendingDataLock.locking {
            val buffered = pendingData.remove(channelId)
            if (buffered != null) {
                for (buf in buffered) {
                    target.income.trySend(buf)
                }
            }
        }
    }

    override suspend fun accept(): DuplexChannel {
        val incomeChannelId = incomeChannels.receive()
        val chanelJob = VirtualChannel(
            id = incomeChannelId,
        )
        activeChannelsMutex.withLock {
            activeChannels[incomeChannelId] = chanelJob
        }
        drainPendingData(incomeChannelId, chanelJob)
        MultiplexerProtocol.sendResponseNewChannel(channelId = incomeChannelId, physical = output)
        return chanelJob
    }

    private inner class VirtualChannel(
        private val id: Int,
    ) : DuplexChannel, AutoCloseable {
        override val income = Channel<Buffer>(Channel.UNLIMITED)
        override val outcome = Channel<Buffer>(Channel.UNLIMITED)
        private val job = ioCoroutineScope.launch(CoroutineName("Output channel $id")) {
            try {
                MultiplexerProtocol.coppingLogicalToPhysical(
                    channelId = id,
                    logical = outcome,
                    physical = output,
                )
            } catch (e: Throwable){
                logger.error(e) { "Error on channel $id copy finished!" }
            } finally {
                val e = CancellationException("Closed by outcome channel closed")
                outcome.close(e)
                income.cancel(e)
                try {
                    MultiplexerProtocol.sendCloseChannel(channelId = id, physical = output)
                } catch (_: Throwable) {
                    // best-effort — close notification may fail if output is already closed
                }
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
        val newChannelId = idGenerator.addAndFetch(2)

        pendingChannelsMutex.lock()
        try {
            MultiplexerProtocol.sendRequestNewChannel(
                channelId = newChannelId,
                physical = output,
            )
        } catch (e: Throwable) {
            pendingChannelsMutex.unlock()
            throw e
        }
        suspendCancellableCoroutine<Unit> { cont ->
            cont.invokeOnCancellation {
                pendingChannels.remove(newChannelId)
            }
            pendingChannels[newChannelId] = cont
            pendingChannelsMutex.unlock()
        }

        val chanelJob = VirtualChannel(id = newChannelId)
        activeChannelsMutex.withLock {
            activeChannels[newChannelId] = chanelJob
        }
        drainPendingData(newChannelId, chanelJob)
        return chanelJob
    }

    private val readJob = ioCoroutineScope.launch {
        try {
            supervisorScope {
                MultiplexerProtocol.reading(
                    physical = input,
                    handlerOnData = { channelId, data ->
                        val channel = activeChannelsMutex.withLock { activeChannels[channelId] }
                        if (channel == null) {
                            // Канал ещё не зарегистрирован — буферизуем
                            pendingDataLock.locking {
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
                        val channel = activeChannelsMutex.withLock {
                            activeChannels.remove(channelId)
                        }
                        logger.info { "found channel $channel" }
                        channel?.close()
                    },
                    requestChannel = { channelId ->
                        incomeChannels.send(channelId)
                    },
                    newChannelAccepted = { channelId ->
                        val water = pendingChannelsMutex.withLock { pendingChannels.remove(channelId) }
                        if (water == null) {
                            MultiplexerProtocol.sendCloseChannel(channelId = channelId, physical = output)
                        } else {
                            water.resume(Unit)
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
            activeChannelsMutex.withLock {
                activeChannels.values.forEach {
                    it.cancel()          // closes income immediately
                    it.close()           // starts graceful shutdown of outcome
                }
                activeChannels.clear()
            }
            kotlinx.coroutines.runBlocking {
                pendingChannelsMutex.withLock {
                    pendingChannels.values.forEach { it.cancel() }
                    pendingChannels.clear()
                }
            }
            pendingDataLock.locking {
                pendingData.clear()
            }
            incomeChannels.cancel()
            throw e
        }
    }

    override fun close() {
        readJob.cancel()
        kotlinx.coroutines.runBlocking {
            activeChannelsMutex.withLock {
                activeChannels.values.forEach { it.close() }
                activeChannels.clear()
            }
            pendingChannelsMutex.withLock {
                pendingChannels.values.forEach { it.cancel() }
                pendingChannels.clear()
            }
        }
        pendingDataLock.locking {
            pendingData.clear()
        }
        incomeChannels.cancel()
    }
}
