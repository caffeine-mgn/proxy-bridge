package pw.binom

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.atomic.AtomicBoolean
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.coroutines.nonCancellable
import pw.binom.frame.AbstractByteBufferFrameInput
import pw.binom.frame.AbstractByteBufferFrameOutput
import pw.binom.frame.FrameChannel
import pw.binom.frame.FrameInput
import pw.binom.frame.FrameOutput
import pw.binom.frame.FrameResult
import pw.binom.frame.PackageSize
import pw.binom.io.ByteBuffer
import pw.binom.io.empty
import pw.binom.io.holdState
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketClosedException
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.io.useAsync
import pw.binom.logger.Logger
import pw.binom.proxy.TransportChannelId
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

/**
 * Превращает WebSocket подключение в фреймовый канал
 */
@Deprecated(message = "Not use it")
@OptIn(ExperimentalStdlibApi::class)
class WsFrameChannel(
    val channelId: TransportChannelId,
    val con: WebSocketConnection,
    override val bufferSize: PackageSize = PackageSize(DEFAULT_BUFFER_SIZE)
) : FrameChannel {

    override fun toString(): String = "WsFrameChannel($channelId)"

    private val logger by Logger.ofThisOrGlobal

    private val writeBuffer = ByteBuffer(bufferSize.asInt + Short.SIZE_BYTES + 1)
    private val readBuffer = ByteBuffer(bufferSize.asInt + Short.SIZE_BYTES + 1)
    private val closed = AtomicBoolean(false)
    private val writeLock = SpinLock()
    private val readLock = SpinLock()
    private var holder: CancellableContinuation<Unit>? = null

    companion object {
        val DATA: Byte = 0x33
        val HEADER_SIZE = Short.SIZE_BYTES + 1
    }

    override suspend fun asyncClose() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        holder?.resume(Unit)
        writeBuffer.close()
        readBuffer.close()
        con.asyncClose()
    }

    private val frameOut = object : AbstractByteBufferFrameOutput() {
        override val buffer: ByteBuffer
            get() = writeBuffer
    }

    private val frameIn = object : AbstractByteBufferFrameInput() {
        override val buffer: ByteBuffer
            get() = readBuffer
    }

    override suspend fun <T> sendFrame(func: (FrameOutput) -> T): FrameResult<T> {
        writeLock.synchronize {
            if (closed.getValue()) {
                return FrameResult.Companion.closed()
            }
            val result = try {
                writeBuffer.clear()
                writeBuffer.put(DATA)
                writeBuffer.writeShort(0)
                func(frameOut)
            } catch (_: Throwable) {
                return FrameResult.Companion.closed()
            }
            try {
                con.write(MessageType.BINARY).useAsync { out ->
                    val size = (writeBuffer.position - HEADER_SIZE).toUShort()
                    writeBuffer.holdState {
                        it.reset(1, Short.SIZE_BYTES)
                        it.writeShort(size.toShort())
                    }
                    writeBuffer.flip()
                    out.writeFully(writeBuffer)
                }
                return FrameResult.Companion.of(result)
            } catch (_: Throwable) {
                return FrameResult.Companion.closed()
            }
        }
    }

    private enum class ReadState {
        LOOP,
        READY_FOR_READ,
        CLOSE,
    }

    override suspend fun <T> readFrame(func: (FrameInput) -> T): FrameResult<T> {
        if (closed.getValue()) {
            return FrameResult.Companion.closed()
        }
        while (coroutineContext.isActive) LOOP@ {
            val sendPackage = readLock.synchronize {
                if (closed.getValue()) {
                    return FrameResult.Companion.closed()
                }
                val msg = try {
                    con.read()
                } catch (_: WebSocketClosedException) {
                    closed.setValue(true)
                    return FrameResult.Companion.closed()
                }



                msg.useAsync { msg ->
                    when (msg.type) {
                        MessageType.PING -> {
                            nonCancellable {
                                con.write(MessageType.PONG).useAsync { out ->
                                    msg.copyTo(out)
                                }
                            }
                            ReadState.LOOP
                        }

                        MessageType.CLOSE -> {
                            ReadState.CLOSE
                        }

                        else -> {
                            nonCancellable {
                                val cmd = msg.readByte(readBuffer)
                                if (cmd != DATA) {
                                    println("First byte should be DATA (0x${DATA.toUByte().toString(16)})")
                                    TODO("Income not data")
                                }
                                val len = PackageSize(msg.readShort(readBuffer))
                                if (len.isZero) {
                                    readBuffer.empty()
                                } else {
                                    readBuffer.reset(0, len.asInt)
                                    msg.readFully(readBuffer)
                                    readBuffer.flip()
                                }
                            }
                            ReadState.READY_FOR_READ
                        }
                    }
                }
            }
            when (sendPackage) {
                ReadState.READY_FOR_READ -> return FrameResult.Companion.of(func(frameIn))
                ReadState.CLOSE -> break
                ReadState.LOOP -> continue
            }
        }
        asyncClose()
        return FrameResult.Companion.closed()
    }

    suspend fun awaitClose() {
        if (holder != null) {
            throw IllegalStateException("Closing awaiter already set")
        }
        if (closed.getValue()) {
            return
        }
        readLock.unlock()
        suspendCancellableCoroutine {
            holder = it
        }
    }
}
