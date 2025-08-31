package pw.binom.frame.virtual

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.RENDEZVOUS
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.*
import pw.binom.atomic.AtomicInt
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.frame.*
import pw.binom.io.AsyncCloseable
import pw.binom.io.ByteBuffer
import pw.binom.io.ByteBufferProvider
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.infoSync
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds


class VirtualChannelManagerImpl3(
    private val source: FrameChannel,
    private val context: CoroutineContext,
    val serverMode: Boolean,
) : AsyncCloseable {
    private val newWaters = HashMap<ChannelId, CancellableContinuation<Unit>>()
    private val newWatersLock = SpinLock()
    private val channelIdIterator = AtomicInt(if (serverMode) 0 else 1)
    private val incomeChannels = Channel<ChannelId>(capacity = 300)
    private val listenersLock = SpinLock()
    private val listeners = HashMap<ChannelId, VirtualFrameReceiver>()
    private val logger by Logger.ofThisOrGlobal

    suspend fun accept(): ChannelId {
        logger.info("Try to accept channel...")
        val channelId = incomeChannels.receive()
        logger.info("Channel $channelId accepted!")
        VirtualManagerMessage.ChannelAccept.send(channelId, source).ensureNotClosed()
        return channelId
    }

    suspend fun new(): ChannelId {
        val newChannelId = channelIdIterator.addAndGet(2)
        logger.info("Creating new virtual channel. id=$newChannelId")
        val newChannel = ChannelId(newChannelId.toShort())
        logger.info("Sending new channel message...")
        VirtualManagerMessage.NewChannel.send(newChannel, source).ensureNotClosed()
        logger.info("Waiting new channel accept...")
        timeoutChecker(timeout = 10.seconds, onTimeout = {
            logger.infoSync("Timeout new channel $newChannel")
        }) {
            suspendCancellableCoroutine { con ->
                con.invokeOnCancellation {
                    newWatersLock.synchronize {
                        newWaters.remove(newChannel)
                    }
                }
                newWatersLock.synchronize {
                    newWaters[newChannel] = con
                }
            }
        }
        logger.info("New channel accepted! id=$newChannelId")
        return newChannel
    }

    private inner class VirtualFrameReceiver(
        val channelId: ChannelId,
        override val bufferSize: PackageSize,
        capacity: Int,
    ) : FrameReceiverWithMeta {
        override val meta: MutableMap<String, String> = HashMap()

        override fun toString(): String = "Channel #${channelId.raw}: $meta"

        private val internalChannel = Channel<ByteBuffer>(
            capacity = capacity,
            onUndeliveredElement = { it.close() }
        )
        val input: SendChannel<ByteBuffer>
            get() = internalChannel

        override suspend fun <T> readFrame(func: (FrameInput) -> T): FrameResult<T> {
            val buf = try {
                internalChannel.receive()
            } catch (_: ClosedReceiveChannelException) {
                return FrameResult.closed()
            }
            return FrameResult.of(func(BufferFrameInput(buf)))
        }

        suspend fun internalClose() {
            closeChannel2(channelId)
            internalChannel.close()
        }

        override suspend fun asyncClose() {
            closeChannel(channelId)
            internalChannel.close()
        }
    }

    suspend fun <T> send(channelId: ChannelId, func: (FrameOutput) -> T): FrameResult<T> {
        val buffer = ByteBuffer((source.bufferSize - 1 - ChannelId.SIZE_BYTES).asInt)
        val result = func(BufferFrameOutput(buffer))
        buffer.flip()
        val msg = VirtualManagerMessage.ChannelData(channelId, buffer)
        logger.info("Sending $msg")
        val vvv = timeoutChecker(timeout = 5.seconds, onTimeout = {
            logger.infoSync("Timeout push message $msg")
        }) { msg.write(source) }
        if (vvv.isClosed) {
            return FrameResult.closed()
        }
        return FrameResult.of(result)
//        VirtualManagerMessage.ChannelData.send(channelId, source) {
//            func(it)
//        }
    }

    suspend fun closeChannel(channelId: ChannelId): FrameResult<Unit> {
        val r = closeChannel2(channelId)
        listenersLock.synchronize {
            listeners.remove(channelId)
        }?.asyncClose()
        return r
    }

    suspend fun closeChannel2(channelId: ChannelId) =
        VirtualManagerMessage.ChannelClosed(channelId).write(source)

    fun listen(channelId: ChannelId, capacity: Int = UNLIMITED): FrameReceiverWithMeta = listenersLock.synchronize {
        check(!listeners.containsKey(channelId)) { "Listener of channel $channelId is already registered" }
        val c = VirtualFrameReceiver(
            bufferSize = source.bufferSize - 1 - ChannelId.SIZE_BYTES,
            capacity = capacity,
            channelId = channelId,
        )
        listeners[channelId] = c
        c
    }

    private var job: Job? = null

    fun start() {
        check(job == null) { "Job is already started" }
        job = GlobalScope.launch(context + CoroutineName("VirtualChannelManager loop")) {
            startReading()
        }
    }

    private suspend fun startReading() {
        val pool = object : ByteBufferProvider {
            override fun get(): ByteBuffer = ByteBuffer(source.bufferSize.asInt - 1 - ChannelId.SIZE_BYTES)

            override fun reestablish(buffer: ByteBuffer) {
                buffer.close()
            }
        }

        suspend fun processing(it: VirtualManagerMessage) {
            logger.info("Income $it")
            when (it) {
                is VirtualManagerMessage.ChannelAccept -> {
                    val waiter = newWatersLock.synchronize {
                        newWaters.remove(it.channelId)
                    }
                    if (waiter == null) {
                        VirtualManagerMessage.ChannelClosed(it.channelId).write(source).ensureNotClosed()
                    } else {
                        waiter.resume(Unit)
                    }
                }

                is VirtualManagerMessage.ChannelClosed -> listenersLock.synchronize {
                    listeners.remove(it.channelId)?.internalClose()
                }

                is VirtualManagerMessage.ChannelData -> {
                    val virtualChannel = listenersLock.synchronize {
                        listeners[it.channelId]
                    }
                    if (virtualChannel != null) {
                        try {
                            timeoutChecker(timeout = 10.seconds, onTimeout = {
                                logger.infoSync("Timeout pushing data to channel $virtualChannel")
                            }) {
                                virtualChannel.input.send(it.data)
                            }
                        } catch (_: ClosedSendChannelException) {
                            virtualChannel.asyncClose()
                            it.data.close()
                        }
                    } else {
                        VirtualManagerMessage.ChannelClosed.send(it.channelId, source)
                    }
                }

                is VirtualManagerMessage.NewChannel -> timeoutChecker(timeout = 10.seconds, onTimeout = {
                    logger.infoSync("Timeout processing income new channel ${it.channelId}")
                }) {
                    incomeChannels.send(it.channelId)
                }
            }
        }

        while (coroutineContext.isActive) {
            val msg = VirtualManagerMessage.read(
                input = source,
                pool = pool,
            )
            if (msg.isClosed) {
                break
            }
            timeoutChecker(timeout = 5.seconds, onTimeout = {
                logger.infoSync("Timeout processing income ${msg.getOrThrow()}")
            }) {
                processing(msg.getOrThrow())
            }
        }
    }

    override suspend fun asyncClose() {
        listenersLock.synchronize {
            listeners.values.forEach {
                it.internalClose()
            }
            listeners.clear()
        }
        job?.cancelAndJoin()
    }

    private class BufferFrameInput(override val buffer: ByteBuffer) : AbstractByteBufferFrameInput()
    private class BufferFrameOutput(override val buffer: ByteBuffer) : AbstractByteBufferFrameOutput()
}


