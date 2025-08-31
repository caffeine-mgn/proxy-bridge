package pw.binom.frame.virtual

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import pw.binom.ChannelId
import pw.binom.frame.*
import pw.binom.io.ByteBuffer
import pw.binom.io.ByteBufferProvider
import kotlin.coroutines.coroutineContext

//fun FrameReceiver.readFlow() = flow<VirtualManagerMessage> {
//    val pool = GenericObjectPool(
//        factory = ByteBufferFactory(bufferSize.asInt - ChannelId.SIZE_BYTES - 1),
//    )
//    try {
//        while (coroutineContext.isActive) {
//            val r = VirtualManagerMessage.read(
//                input = this@readFlow,
//                pool = pool
//            )
//            if (r.isClosed) {
//                break
//            }
//        }
//    } finally {
//        pool.closeAnyway()
//    }
//}

sealed interface VirtualManagerMessage {
    companion object {
        private const val CHANNEL_DATA: Byte = 1
        private const val CHANNEL_CLOSED: Byte = 2
        private const val NEW_CHANNEL: Byte = 3
        private const val CHANNEL_ACCEPTED: Byte = 4

        fun flow(receiver: FrameReceiver, pool: ByteBufferProvider) = flow<VirtualManagerMessage> {
//            val pool = GenericObjectPool(
//                factory = ByteBufferFactory(receiver.bufferSize.asInt),
//            )
            while (coroutineContext.isActive) {
                println("---------->Reading....")
                val r = try {
                    VirtualManagerMessage.read(
                        input = receiver,
                        pool = pool,
                    )
                } catch (e: Throwable) {
                    e.printStackTrace()
                    throw e
                }

                if (r.isClosed) {
                    break
                }
                try {
                    emit(r.getOrThrow())
                } catch (e: Throwable) {
                    e.printStackTrace()
                    throw e
                }
            }
        }

        suspend fun read(input: FrameReceiver, pool: ByteBufferProvider) =
            input.readFrame {
                read(it, pool)
            }

        fun read(input: FrameInput, pool: ByteBufferProvider) =
            when (val cmd = input.readByte()) {
                CHANNEL_DATA -> {
                    val channelId = ChannelId.read(input)
                    val b = pool.get()
                    b.clear()
                    input.readInto(b)
                    b.flip()
                    ChannelData(
                        channelId = channelId,
                        data = b,
                    )
                }

                CHANNEL_CLOSED -> ChannelClosed(ChannelId.read(input))
                NEW_CHANNEL -> NewChannel(ChannelId.read(input))
                CHANNEL_ACCEPTED -> ChannelAccept(ChannelId.read(input))
                else -> throw IllegalStateException("Unknown command $cmd")
            }
    }

    /**
     * Данные с канала
     */
    data class ChannelData(val channelId: ChannelId, val data: ByteBuffer) : VirtualManagerMessage {
        companion object {

            fun writeHead(
                channelId: ChannelId,
                output: FrameOutput,
            ) {
                output.writeByte(CHANNEL_DATA)
                channelId.write(output)
            }

            suspend fun <T> send(
                channelId: ChannelId,
                output: FrameSender,
                func: (buffer: FrameOutput) -> T
            ): FrameResult<T> =
                output.sendFrame<T> {
                    writeHead(channelId, it)
                    func(it)
                }
        }

        private val size = data.remaining
        override fun toString() = "ChannelData(channelId=${channelId.raw}, data: $size bytes)"

        override fun write(output: FrameOutput) {
            writeHead(channelId, output)
            output.writeFrom(data)
        }
    }

    /**
     * Запрос создания нового канала
     */
    data class NewChannel(val channelId: ChannelId) : VirtualManagerMessage {
        companion object {
            fun send(channelId: ChannelId, output: FrameOutput) {
                output.writeByte(NEW_CHANNEL)
                channelId.write(output)
            }

            suspend fun send(channelId: ChannelId, output: FrameSender) =
                output.sendFrame { send(channelId, it) }
        }

        override fun write(output: FrameOutput) {
            send(channelId, output)
        }
    }

    /**
     * Ответ созданного канала
     */
    data class ChannelAccept(val channelId: ChannelId) : VirtualManagerMessage {
        companion object {
            fun send(channelId: ChannelId, output: FrameOutput) {
                output.writeByte(CHANNEL_ACCEPTED)
                channelId.write(output)
            }

            suspend fun send(channelId: ChannelId, output: FrameSender) =
                output.sendFrame { send(channelId, it) }
        }

        override fun write(output: FrameOutput) {
            send(channelId = channelId, output = output)
        }
    }

    /**
     * Уведомление о закрытии канала
     */
    data class ChannelClosed(val channelId: ChannelId) : VirtualManagerMessage {
        companion object {
            fun send(channelId: ChannelId, output: FrameOutput) {
                output.writeByte(CHANNEL_CLOSED)
                channelId.write(output)
            }

            suspend fun send(channelId: ChannelId, output: FrameSender) =
                output.sendFrame { send(channelId, it) }
        }

        override fun write(output: FrameOutput) {
            send(channelId, output)
        }
    }

    fun write(output: FrameOutput)
    suspend fun write(output: FrameSender) = output.sendFrame {
        write(it)
    }
}
