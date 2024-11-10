package pw.binom

import pw.binom.atomic.AtomicBoolean
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.frame.FrameChannel
import pw.binom.frame.FrameInput
import pw.binom.frame.FrameOutput
import pw.binom.frame.FrameResult
import pw.binom.frame.PackageSize
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.infoSync

/**
 * Синхронно закрываемый фреймовый канал
 */
class AutoCloseFrameChannel(
    val channel: FrameChannel,
    val id: Int,
) : FrameChannel {
    private var logger = Logger.getLogger("AutoCloseFrameChannel $id")
    override val bufferSize: PackageSize
        get() = channel.bufferSize - 1 - Int.SIZE_BYTES

    companion object {
        const val DATA: Byte = 0x0a
        const val CLOSE: Byte = 0x0b
    }

    private object CLOSE_MARKER

    private val closed = AtomicBoolean(false)
    private val closeFlagReceived = AtomicBoolean(false)
    val isClosed
        get() = closed.getValue()

    private suspend fun readUntilClose() {
        if (!closeFlagReceived.compareAndSet(false, true)) {
            return
        }
        logger.info("Reading until end!")
        while (true) {
            val l = channel.readFrame {
                when (val cmd = it.readByte()) {
                    CLOSE -> {
                        val remoteId = it.readInt()
                        logger.infoSync("Found close flag! Stop reading until end!")
                        true
                    }

                    DATA -> {
                        val remoteId = it.readInt()
                        false
                    }

                    else -> TODO("Unknown CMD $cmd 0x${cmd.toUByte().toString(16)}")
                }
            }
            if (l.isClosed || l.getOrThrow()) {
                break
            }
        }
    }

    override suspend fun <T> sendFrame(func: (FrameOutput) -> T): FrameResult<T> =
        if (!closed.getValue() && !closeFlagReceived.getValue()) {
            channel.sendFrame {
                it.writeByte(DATA)
                it.writeInt(id)
                func(it)
            }
        } else {
            FrameResult.Companion.closed()
        }

    private val readingLock = SpinLock()

    override suspend fun <T> readFrame(func: (FrameInput) -> T): FrameResult<T> {
        readingLock.synchronize {
            while (true) {
                val result = channel.readFrame<Any?> { buf ->
                    when (val cmd = buf.readByte()) {
                        CLOSE -> {
                            val remoteId = buf.readInt()
                            logger.infoSync("Received close flag. ID-OK=${remoteId == id}")
                            closeFlagReceived.setValue(true)
                            return@readFrame CLOSE_MARKER
                        }

                        DATA -> {
                            val remoteId = buf.readInt()
                            if (id != remoteId) {
                                logger.infoSync("Received data id not is OK. id=$id, remoteId=$remoteId")
                            }
                            func(buf)
                        }

                        else -> {
                            logger.infoSync("Unknown package $cmd 0x${cmd.toUByte().toString(16)}")
                            TODO("Unknown package $cmd 0x${cmd.toUByte().toString(16)}")
                        }
                    }
                }
                if (result.isClosed) {
                    logger.info("Can't read data. Stream closed")
                    closeFlagReceived.setValue(true)
                    closed.setValue(true)
                    return result as FrameResult<T>
                }
                if (closed.getValue()) {
                    readUntilClose()
                    return FrameResult.Companion.closed()
                }
                if (result.getOrThrow() === CLOSE_MARKER) {
                    readUntilClose()
                    asyncClose()
                    return FrameResult.Companion.closed()
                }
                return result as FrameResult<T>
            }
        }
    }

    override suspend fun asyncClose() {
        if (closed.compareAndSet(false, true)) {
            logger.info("Send close flag")
            channel.sendFrame {
                it.writeByte(CLOSE)
                it.writeInt(id)
            }
        }
        if (readingLock.tryLock()) {
            try {
                readUntilClose()
            } finally {
                readingLock.unlock()
            }
        }
    }

}
