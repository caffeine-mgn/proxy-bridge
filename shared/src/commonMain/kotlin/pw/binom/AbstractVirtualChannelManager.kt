package pw.binom

import pw.binom.atomic.AtomicBoolean
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.frame.AbstractByteBufferFrameInput
import pw.binom.frame.FrameInput
import pw.binom.frame.FrameOutput
import pw.binom.frame.FrameResult
import pw.binom.frame.PackageSize
import pw.binom.frame.virtual.VirtualChannel
import pw.binom.io.ByteBuffer
import pw.binom.io.Closeable
import pw.binom.io.use
import pw.binom.logger.Logger
import pw.binom.logger.infoSync

@Deprecated(message = "Not use it")
abstract class AbstractVirtualChannelManager : VirtualChannelManager {

    companion object {
        const val CLOSE: Byte = 0x1
        const val DATA: Byte = 0x2
        const val HEADER_SIZE = Byte.SIZE_BYTES + ChannelId.SIZE_BYTES
    }

    protected val logger by Logger.ofThisOrGlobal
    protected abstract suspend fun <T> pushFrame(func: (FrameOutput) -> T): FrameResult<T>
    protected abstract val bufferSize: PackageSize

    private val channels = HashMap<ChannelId, VirtualChannelImpl>()
    private val channelLock = SpinLock()

    /**
     * Вызывается когда приходят данные от не известного канала
     */
    protected abstract fun emittedNewChannel(id: ChannelId, channel: VirtualChannel)

    override val channelCount: Int
        get() = channelLock.synchronize {
            channels.size
        }

    override fun getOrCreateChannel(id: ChannelId): VirtualChannel =
        channelLock.synchronize {
            channels.getOrPut(id) { VirtualChannelImpl(id) }
        }

//    override fun getChannel(id: ChannelId): VirtualChannelManager.VirtualChannel? =
//        channelLock.synchronize {
//            channels[id]
//        }


    fun income(input: FrameInput) {
        val action = input.readByte() // читаем команду
        val virtualChannelId = ChannelId(input.readShort()) // читаем id канала
        val channel = channelLock.synchronize {
            val exist = channels[virtualChannelId]
            if (exist == null) {
                logger.infoSync("Creating channel $virtualChannelId")
                val new = VirtualChannelImpl(virtualChannelId)
                channels[virtualChannelId] = new
                emittedNewChannel(
                    id = new.id,
                    channel = new,
                )
                new
            } else {
                exist
            }
        }
        when (action) {
            // пришли данные
            DATA -> channel.incomePackage(input)
            // если нам приходит, что канал закрыт
            CLOSE -> {
                if (channel != null) { // если канал существует
                    channelLock.synchronize {
                        channels.remove(virtualChannelId) // удаляем его из управляемых
                    }
                    channel.internalClose() // делаем канал закрытым
                }
            }
        }
    }

    override suspend fun asyncClose() {
        channelLock.synchronize {
            channels.values.forEach {
                it.internalClose()
            }
            channels.clear()
        }
    }

    private class BufferFrameInput(override val buffer: ByteBuffer) : AbstractByteBufferFrameInput(), Closeable {
        override fun close() {
            buffer.close()
        }
    }


    @Suppress("UNCHECKED_CAST")
    private inner class VirtualChannelImpl(override val id: ChannelId) :
        VirtualChannel {

        override fun toString(): String = "VirtualChannel(id=$id)"

        override val bufferSize: PackageSize = this@AbstractVirtualChannelManager.bufferSize - HEADER_SIZE

        //        private var readWater: CancellableContinuation<BufferFrameInput?>? = null
        private val incomes = AsyncQueue<BufferFrameInput?>()
        private val closed = AtomicBoolean(false)
        private val closedMarker = AtomicBoolean(false)

        fun internalClose(): Boolean {
            if (closed.getValue() || !closedMarker.compareAndSet(false, true)) {
                return false
            }
            pushInput(null)
            return true
        }

        private fun pushInput(input: BufferFrameInput?) {
            incomes.push(input)
        }

        fun incomePackage(input: FrameInput) {
            if (closed.getValue()) {
                return
            }
            val buf = ByteBuffer(bufferSize.asInt)
            input.readInto(buf)
            buf.flip()
            val cloned = BufferFrameInput(buf)
            pushInput(cloned)
        }

        override suspend fun <T> sendFrame(func: (FrameOutput) -> T): FrameResult<T> {
            if (closedMarker.getValue()) {
                return FrameResult.Companion.closed()
            }
            val r = pushFrame { out ->
                out.writeByte(DATA) // посылаем команду
                out.writeShort(id.raw) // посылаем id канала
                func(out) // даём записать данные в поток
            }
            if (r.isClosed) {
                internalClose()
                removeSelf()
                return FrameResult.Companion.closed()
            }
            return r
        }

        override suspend fun <T> readFrame(func: (FrameInput) -> T): FrameResult<T> {
            if (closed.getValue()) {
                return FrameResult.Companion.closed()
            }
            val frameInput = incomes.pop()
            if (frameInput == null) { // если оказалось, что поток окончен
                realClose()
                removeSelf()
                return FrameResult.Companion.closed()
            }
            // если поток не окончен, то честно читаем сообщение
            return frameInput.use { input ->
                FrameResult.Companion.of(func(frameInput))
            }
        }

        private fun removeSelf() {
            channelLock.synchronize {
                channels.remove(id)
            }
        }

        private fun realClose() {
            if (!closed.compareAndSet(false, true)) {
                return
            }
            incomes.locking {
                it.forEach {
                    it?.close()
                }
                it.clear()
            }
        }

        override suspend fun asyncClose() {
            if (internalClose()) {
                pushFrame { out ->
                    out.writeByte(CLOSE) // посылаем команду
                    out.writeShort(id.raw) // посылаем id канала
                }
                removeSelf()
            }

        }
    }


}

