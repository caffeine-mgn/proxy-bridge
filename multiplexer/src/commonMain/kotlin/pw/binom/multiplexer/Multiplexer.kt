package pw.binom.multiplexer

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.io.Buffer
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resume

@OptIn(ExperimentalAtomicApi::class)
class Multiplexer(
    val input: ReceiveChannel<Buffer>,
    val output: SendChannel<Buffer>,
    idOdd: Boolean,
    private val ioCoroutineScope: CoroutineScope
) : AutoCloseable {
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
    private val activeChannelsLock = AtomicBoolean(false)
    private val activeChannels = HashMap<Int, VirtualChannel>()
    private val pendingChannelsLock = AtomicBoolean(false)
    private val pendingChannels = HashMap<Int, CancellableContinuation<Unit>>()
    private val incomeChannels = Channel<Int>(Channel.UNLIMITED)

    suspend fun accept(): DuplexChannel {
        val incomeChannelId = incomeChannels.receive()
        val chanelJob = VirtualChannel(
            id = incomeChannelId,
        )
        activeChannelsLock.locking {
            activeChannels[incomeChannelId] = chanelJob
        }
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
            } finally {
                val e = CancellationException("Closed by outcome channel closed")
                outcome.close(e)
                income.cancel(e)
                MultiplexerProtocol.sendCloseChannel(channelId = id, physical = output)
            }
        }

        init {
            income.invokeOnClose {
                job.cancel()
            }
        }

        override fun close() {
            job.cancel()
        }
    }


    suspend fun createChannel(): DuplexChannel {
        val newChannelId = idGenerator.addAndFetch(2)

        pendingChannelsLock.lock()
        MultiplexerProtocol.sendRequestNewChannel(
            channelId = newChannelId,
            physical = output,
        )
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation {
                pendingChannelsLock.locking {
                    pendingChannels.remove(newChannelId)
                }
            }
            pendingChannels[newChannelId] = cont
            pendingChannelsLock.unlock()
        }

        val chanelJob = VirtualChannel(id = newChannelId)
        activeChannelsLock.locking {
            activeChannels[newChannelId] = chanelJob
        }
        return chanelJob
    }

    private val readJob = ioCoroutineScope.launch {
        MultiplexerProtocol.reading(
            physical = input,
            handlerOnData = { channelId, data ->
                val channel = activeChannelsLock.locking { activeChannels[channelId] }
                if (channel == null) {
                    MultiplexerProtocol.sendCloseChannel(channelId = channelId, physical = output)
                } else {
                    channel.income.send(data)
                }
            },
            channelClosed = { channelId ->
                println("Income message for close channel $channelId")
                val channel = activeChannelsLock.locking {
                    activeChannels.remove(channelId)
                }
                println("found channel $channel")
                channel?.close()
            },
            requestChannel = { channelId ->
                incomeChannels.send(channelId)
            },
            newChannelAccepted = { channelId ->
                val water = pendingChannelsLock.locking { pendingChannels.remove(channelId) }
                if (water == null) {
                    MultiplexerProtocol.sendCloseChannel(channelId = channelId, physical = output)
                } else {
                    water.resume(Unit)
                }
            },
        )
    }

    override fun close() {
        readJob.cancel()
        activeChannelsLock.locking {
            activeChannels.values.forEach { it.close() }
            activeChannels.clear()
        }
        pendingChannelsLock.locking {
            pendingChannels.values.forEach { it.cancel() }
            pendingChannels.clear()
        }
        incomeChannels.cancel()
    }
}
