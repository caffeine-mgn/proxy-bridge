package pw.binom.io
/*
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.Buffer
import pw.binom.frame.PackageSize
import pw.binom.utils.lock
import pw.binom.utils.locking
import pw.binom.utils.unlock
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resume

@OptIn(ExperimentalAtomicApi::class)
class Multiplexer(
    private val input: VirtualInput,
    private val output: VirtualOutput,
    private val scope: CoroutineScope,
    private var maxBufferSize: PackageSize,
    idOdd: Boolean,
) {
    companion object {
        private val REQUEST_NEW_CHANNEL = 1.toByte()
        private val ACCEPT_NEW_CHANNEL = 2.toByte()
        private val DATA_CHANNEL = 3.toByte()
        private val CLOSE_CHANNEL = 4.toByte()
        private val REJECT_CHANNEL = 5.toByte()
    }

    private val idGenerator = AtomicInt(if (idOdd) 1 else 0)
    private val newChannelLock = AtomicBoolean(false)
    private val сhannelsLock = AtomicBoolean(false)
    private val newChannelWaiter = HashMap<Short, CancellableContinuation<Unit>>()
    private val channels = HashMap<Short, VirtualChannel>()
    private val dataJobs = HashMap<Short, kotlinx.coroutines.Job>()

    private val readJob = scope.launch {
        while (isActive) {
            val cmd = input.readByte()
            when (cmd) {
                ACCEPT_NEW_CHANNEL -> newChannelLock.locking {
                    val channelId = input.readShort()
                    newChannelWaiter.remove(channelId)?.resume(Unit)
                }
                DATA_CHANNEL -> {
                    val channelId = input.readShort()
                    val size = input.readShort()
                    val data = ByteArray(size.toInt())
                    for (i in 0 until size.toInt()) {
                        data[i] = input.readByte()
                    }
                    сhannelsLock.locking {
                        channels[channelId]?.let { virtualChannel ->
                            val buffer = Buffer()
                            data.forEach { buffer.writeByte(it) }
                            try {
                                virtualChannel.input.sendChannel.send(buffer)
                            } catch (_: Exception) {
                            }
                        } ?: run { input.close() }
                    }
                }
                CLOSE_CHANNEL -> {
                    val channelId = input.readShort()
                    сhannelsLock.locking {
                        dataJobs.remove(channelId)?.cancel()
                        channels.remove(channelId)?.let { virtualChannel ->
                            virtualChannel.input.close()
                            virtualChannel.output.close()
                        }
                    }
                }
                REJECT_CHANNEL -> {
                    val channelId = input.readShort()
                    newChannelWaiter.remove(channelId)?.resume(Unit)
                }
            }
        }
    }

    suspend fun createChannel(): VirtualChannel {
        val newId = idGenerator.addAndFetch(2).toShort()
        output.writeByte(REQUEST_NEW_CHANNEL)
        output.writeShort(newId)
        newChannelLock.lock()
        output.flush()
        suspendCancellableCoroutine { continuation ->
            newChannelWaiter[newId] = continuation
            newChannelLock.unlock()
        }
        val inChannel = Channel<Buffer>(capacity = Channel.UNLIMITED)
        val inputSendChannel = Channel<Buffer>(capacity = Channel.UNLIMITED)
        val outputChannel = Channel<Buffer>(capacity = Channel.UNLIMITED)

        val virtualChannel = VirtualChannel(
            VirtualInput(inChannel, inputSendChannel),
            VirtualOutput(outputChannel, maxBufferSize)
        )
        сhannelsLock.locking {
            channels[newId] = virtualChannel
        }
        val dataJob = scope.launch {
            try {
                for (buffer in outputChannel) {
                    output.writeByte(DATA_CHANNEL)
                    output.writeShort(newId)
                    output.writeShort(buffer.size.toShort())
                    output.write(buffer, buffer.size)
                    output.flush()
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
            }
        }
        сhannelsLock.locking {
            dataJobs[newId] = dataJob
        }
        return virtualChannel
    }

    fun close() {
        readJob.cancel()
        сhannelsLock.locking {
            channels.values.forEach {
                it.input.close()
                it.output.close()
            }
            channels.clear()
            dataJobs.clear()
        }
    }
}


 */
