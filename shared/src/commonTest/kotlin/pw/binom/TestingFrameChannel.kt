package pw.binom

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.atomic.AtomicBoolean
import pw.binom.atomic.AtomicInt
import pw.binom.collections.LinkedList
import pw.binom.concurrency.SpinLock
import pw.binom.frame.ByteBufferFrameInput
import pw.binom.frame.ByteBufferFrameOutput
import pw.binom.frame.FrameChannel
import pw.binom.frame.FrameInput
import pw.binom.frame.FrameOutput
import pw.binom.frame.FrameResult
import pw.binom.frame.PackageSize
import pw.binom.io.ByteArrayOutput
import pw.binom.io.ByteBuffer
import kotlin.coroutines.resume

class TestingFrameChannel(override val bufferSize: PackageSize = PackageSize(DEFAULT_BUFFER_SIZE)) : FrameChannel {

    private val inputs = LinkedList<FrameInput>()
    private val lockRead = SpinLock()
    private val lockWrite = SpinLock()
    private val closedFlag = AtomicBoolean(false)
    private val internalWasReadPackages = AtomicInt(0)
    val wasReadPackages
        get() = internalWasReadPackages.getValue()

    class ByteArrayOutputImpl : ByteArrayOutput() {
        operator fun ByteArray.unaryPlus() {
            write(this)
        }

        operator fun Byte.unaryPlus() {
            writeByte(this)
        }

        operator fun Int.unaryPlus() {
            writeInt(this)
        }

        operator fun ByteArray.not() {
            write(this)
        }

        operator fun Byte.not() {
            writeByte(this)
        }

        operator fun Int.not() {
            writeInt(this)
        }
    }

    fun pushInput(func: ByteArrayOutputImpl.() -> Unit) {
        val o = ByteArrayOutputImpl()
        func(o)
        pushInput(ByteBufferFrameInput(ByteBuffer.wrap(o.toByteArray())))
    }

    fun pushInput(input: FrameInput) {
        lockRead.lock()
        val w = inputWater
        if (w != null) {
            inputWater = null
            lockRead.unlock()
            w.resume(input)
        } else {
            inputs.addLast(input)
            lockRead.unlock()
        }
    }

    private var inputWater: CancellableContinuation<FrameInput>? = null
    private var outputWater: CancellableContinuation<ByteArray>? = null
    private val CLOSE_MARKER = object : FrameInput {
        override fun readByte(): Byte {
            TODO("Not yet implemented")
        }
    }
//    private val CLOSE_MARKER2 = object : FrameOutput {
//        override fun writeByte(value: Byte) {
//            TODO("Not yet implemented")
//        }
//    }

    private val outputs = LinkedList<ByteArray>()

    suspend fun popOut(): ByteArray {
        lockWrite.lock()
        val frame = if (outputs.isEmpty()) {
            suspendCancellableCoroutine<ByteArray> {
                outputWater = it
                lockWrite.unlock()
            }
        } else {
            val l = outputs.removeFirst()
            lockWrite.unlock()
            l
        }
        return frame
    }

    override suspend fun <T> sendFrame(func: (FrameOutput) -> T): FrameResult<T> {
        if (closedFlag.getValue()) {
            return FrameResult.Companion.closed()
        }
        val l = ByteBufferFrameOutput(ByteBuffer(bufferSize.asInt))
        val result = func(l)
        l.buffer.flip()
        val data = l.buffer.toByteArray()
        lockWrite.lock()
        val w = outputWater
        if (w != null) {
            outputWater = null
            lockWrite.unlock()
            w.resume(data)
        } else {
            outputs += data
            lockWrite.unlock()
        }
        return FrameResult.Companion.of(result)
    }

    private val closedFlag1 = AtomicBoolean(false)

    override suspend fun <T> readFrame(func: (FrameInput) -> T): FrameResult<T> {
        if (closedFlag1.getValue()) {
            return FrameResult.Companion.closed()
        }
        lockRead.lock()
        val frame = if (inputs.isEmpty()) {
            suspendCancellableCoroutine<FrameInput> {
                inputWater = it
                lockRead.unlock()
            }
        } else {
            val l = inputs.removeFirst()
            lockRead.unlock()
            l
        }
        if (frame === CLOSE_MARKER) {
            closedFlag1.setValue(true)
            return FrameResult.Companion.closed()
        }
        internalWasReadPackages.inc()
        return FrameResult.Companion.of(func(frame))
    }

    override suspend fun asyncClose() {
        if (!closedFlag.compareAndSet(false, true)) {
            return
        }
//        inputs.addLast(CLOSE_MARKER)
//        outputs.addLast(CLOSE_MARKER2)

        pushInput(CLOSE_MARKER)
//        lockRead.lock()
//        val w = inputWater
//        if (w != null) {
//            inputWater = null
//            lockRead.unlock()
//            w.resume(CLOSE_MARKER)
//        } else {
//            lockRead.unlock()
//        }
    }
}
