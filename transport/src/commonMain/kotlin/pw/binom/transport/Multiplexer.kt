package pw.binom.transport

import pw.binom.atomic.AtomicInt
import pw.binom.coroutines.SimpleAsyncLock
import pw.binom.io.AsyncInput
import pw.binom.io.AsyncOutput
import pw.binom.io.ByteBuffer

class Multiplexer(
    private val input: AsyncInput,
    private val output: AsyncOutput,
    serverMode: Boolean,
    maxPackageSize: Int,
) {

    companion object {
        private const val HEADER_SIZE = 1 + 4 + 4
        private const val NEW_CHANNEL: Byte = 1
        private const val CHANNEL_DATA: Byte = 2
        private const val CLOSE_CHANNEL: Byte = 3
        private const val CONNECTION_REFUSED: Byte = 4
        private const val CHANNEL_ACCEPTED: Byte = 6
    }

    sealed interface Event {
        val channelId: Int

        data class Closing(override val channelId: Int) : Event
        data class Accepted(override val channelId: Int) : Event
        data class Refused(override val channelId: Int) : Event
        data class New(override val channelId: Int, val serviceId: Int) : Event
        data class Data(override val channelId: Int, val data: ByteBuffer) : Event
    }

    private var channelIterator = AtomicInt(if (serverMode) 1 else 0)
    private val writeLock = SimpleAsyncLock()
    private val readLock = SimpleAsyncLock()
    val packageSize = maxPackageSize - HEADER_SIZE


    suspend fun new(serviceId: Int) = writeLock.synchronize {
        val newChannelId = channelIterator.addAndGet(2)
        output.writeByte(NEW_CHANNEL)
        output.writeInt(newChannelId)
        output.writeInt(serviceId)
        newChannelId
    }

    suspend fun send(channel: Int, data: ByteBuffer) {
        writeLock.synchronize {
            output.writeByte(CHANNEL_DATA)
            output.writeInt(channel)
            output.writeInt(data.remaining)
            output.writeFully(data)
        }
    }

    suspend fun close(channel: Int) {
        writeLock.synchronize {
            output.writeByte(CLOSE_CHANNEL)
            output.writeInt(channel)
        }
    }

    suspend fun accepted(channel: Int) {
        writeLock.synchronize {
            output.writeByte(CHANNEL_ACCEPTED)
            output.writeInt(channel)
        }
    }

    suspend fun refused(channel: Int) {
        writeLock.synchronize {
            output.writeByte(CONNECTION_REFUSED)
            output.writeInt(channel)
        }
    }

    suspend fun read() = readLock.synchronize {
        val cmd = input.readByte()
        when (cmd) {
            CONNECTION_REFUSED -> Event.Refused(input.readInt())
            CHANNEL_ACCEPTED -> Event.Accepted(input.readInt())
            NEW_CHANNEL -> {
                val channelId = input.readInt()
                val serviceId = input.readInt()
                Event.New(
                    channelId = channelId,
                    serviceId = serviceId,
                )
            }

            CLOSE_CHANNEL -> Event.Closing(input.readInt())
            CHANNEL_DATA -> {
                val channelId = input.readInt()
                val size = input.readInt()
                val data = ByteBuffer(size)
                input.readFully(data)
                data.flip()
                Event.Data(
                    channelId = channelId,
                    data = data,
                )
            }

            else -> TODO("Unknown code: $cmd")
        }
    }
}
