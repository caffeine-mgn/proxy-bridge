package pw.binom.frame.virtual

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.*
import pw.binom.collections.LinkedList
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.frame.*
import pw.binom.io.ByteBuffer
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resume

@OptIn(ExperimentalAtomicApi::class)
class VirtualChannelManagerImpl3(
    val source: FrameChannel,
    val sendChannel: SendChannel<ByteBuffer>,
    val readChannel: ReceiveChannel<ByteBuffer>,
    serverMode: Boolean,
) {
    companion object {
        private const val CMD_DATA: Byte = 1
        private const val CMD_CLOSED: Byte = 1
        private const val NEW_CHANNEL: Byte = 1
        private const val NEW_CHANNEL_ACCEPT: Byte = 1
    }

    private val waitingChannels = HashSet<ChannelId>()
    private val water = LinkedList<CancellableContinuation<VirtualChannel>>()
    private val watersLock = SpinLock()
    private val newWaters = HashMap<ChannelId, CancellableContinuation<Unit>>()
    private val channels = HashMap<ChannelId, VirtualChannel>()
    private val channelsLock = SpinLock()
    private val channelIdIterator = AtomicInt(if (serverMode) 0 else 1)

    suspend fun accept(): FrameChannel {
        watersLock.lock()
        if (waitingChannels.isEmpty()) {
            return suspendCancellableCoroutine { con ->
                con.invokeOnCancellation {
                    watersLock.synchronize {
                        water.remove(con)
                    }
                }
                water.addLast(con)
                watersLock.unlock()
            }
        }
        val channelId = waitingChannels.iterator().next()
        waitingChannels.remove(channelId)
        watersLock.unlock()
        val newChannel = virtualChannel(channelId)
        channelsLock.synchronize {
            channels[channelId] = newChannel
        }
        return newChannel

    }

    private fun virtualChannel(id: ChannelId) =
        VirtualChannel(id, source.bufferSize - (1 + ChannelId.SIZE_BYTES))

    suspend fun new(): FrameChannel {
        val newChannelId = channelIdIterator.addAndFetch(2)
        val newChannel = ChannelId(newChannelId.toShort())
        source.sendFrame {
            it.writeByte(NEW_CHANNEL)
            newChannel.write(it)
        }.ensureNotClosed()
        suspendCancellableCoroutine { con ->
            con.invokeOnCancellation {
                newWaters.remove(newChannel)
            }
            newWaters[newChannel] = con
        }
        val c = virtualChannel(newChannel)
        channelsLock.synchronize {
            channels[newChannel] = c
        }
        return c
    }

    private val readers = HashMap<ChannelId, Channel<ByteBuffer>>()

    fun new(id: ChannelId): ReceiveChannel<ByteBuffer> {
        check(!readers.containsKey(id)) { "Channel $id already exist" }
        val c = Channel<ByteBuffer>()
        readers[id] = c
        return c
    }

    fun <T> send(id: ChannelId, func: (ByteBuffer) -> T): FrameResult<T> {

    }

    sealed interface Res {
        class Data(val channel: ChannelId, val buf: ByteBuffer) : Res
    }


    suspend fun startReading(
        dataInput: ReceiveChannel<ByteBuffer>,
    ) {
        val ff = source.readFrame {
            when (val cmd = it.readByte()) {
                NEW_CHANNEL_ACCEPT -> {
                    val channelId = ChannelId.read(it)
                    newWaters.remove(channelId)?.resume(Unit)
                    null
                }

                NEW_CHANNEL -> {
                    val channelId = ChannelId.read(it)
                    val w = watersLock.synchronize {
                        val w = water.removeFirstOrNull()
                        if (w == null) {
                            waitingChannels.add(channelId)
                            null
                        } else {
                            w
                        }
                    }
                    if (w != null) {
                        val c = virtualChannel(channelId)
                        channelsLock.synchronize {
                            channels[channelId] = c
                        }
                        w.resume(c)
                    }
                    null
                }

                CMD_DATA -> {
                    val channelId = ChannelId.read(it)
                    val buf = ByteBuffer(source.bufferSize.asInt)
                    it.readInto(buf)
                    Res.Data(channel = channelId, buf = buf)
                }

                else -> TODO()
            }
        }
        val data = ff.ensureNotClosed()
        if (data!=null){

        }
    }

    private class BufferFrameInput(override val buffer: ByteBuffer) : AbstractByteBufferFrameInput()

    private class VirtualChannel(val channelId: ChannelId, override val bufferSize: PackageSize) : FrameChannel {
        val income = Channel<ByteBuffer>()
        override suspend fun <T> readFrame(func: (buffer: FrameInput) -> T): FrameResult<T> {
            val buf = income.receive()
            return FrameResult.of(func(BufferFrameInput(buf)))
        }


        override suspend fun asyncClose() {
            TODO("Not yet implemented")
        }

        override suspend fun <T> sendFrame(func: (buffer: FrameOutput) -> T): FrameResult<T> {
            TODO("Not yet implemented")
        }

    }
}
