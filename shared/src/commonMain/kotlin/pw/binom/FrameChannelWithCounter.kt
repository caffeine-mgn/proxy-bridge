package pw.binom

import pw.binom.atomic.AtomicLong
import pw.binom.frame.FrameChannel
import pw.binom.frame.FrameInput
import pw.binom.frame.FrameOutput
import pw.binom.frame.FrameResult
import pw.binom.frame.PackageSize
import pw.binom.io.ByteBuffer

@Deprecated(message = "Not use it")
class FrameChannelWithCounter(val other: FrameChannel) : FrameChannel {
    override val bufferSize: PackageSize
        get() = other.bufferSize
    private var internalWrite = AtomicLong(0)
    private var internalRead = AtomicLong(0)

    init {
        println("Start $other")
    }

    val read
        get() = internalRead.getValue()

    val write
        get() = internalWrite.getValue()

    override suspend fun <T> sendFrame(func: (FrameOutput) -> T): FrameResult<T> {
        val result = other.sendFrame { buffer ->
            func(object : FrameOutput {
                override fun writeByte(value: Byte) {
                    buffer.writeByte(value)
                    internalWrite.inc()
                }

                override fun writeFrom(src: ByteBuffer): Int {
                    val size = buffer.writeFrom(src)
                    internalWrite.addAndGet(size.toLong())
                    return size
                }

                override fun writeInt(value: Int) {
                    internalWrite.addAndGet(4)
                    buffer.writeInt(value)
                }

                override fun writeShort(value: Short) {
                    internalWrite.addAndGet(2)
                    buffer.writeShort(value)
                }

                override fun writeByteArray(data: ByteArray): Int {
                    internalWrite.addAndGet(data.size.toLong())
                    return buffer.writeByteArray(data)
                }
            })
        }
        return result
    }

    override suspend fun <T> readFrame(func: (FrameInput) -> T): FrameResult<T> {
        val result = other.readFrame { buffer ->
            func(object : FrameInput {
                override fun readByte(): Byte {
                    val b = buffer.readByte()
                    internalRead.inc()
                    return b
                }

                override fun readByteArray(size: Int): ByteArray {
                    val l = buffer.readByteArray(size)
                    internalRead.addAndGet(size.toLong())
                    return l
                }

                override fun readByteArray(dest: ByteArray) {
                    buffer.readByteArray(dest)
                    internalRead.addAndGet(dest.size.toLong())
                }

                override fun readInt(): Int {
                    val l = buffer.readInt()
                    internalRead.addAndGet(4)
                    return l
                }

                override fun readShort(): Short {
                    val l = buffer.readShort()
                    internalRead.addAndGet(2)
                    return l
                }

                override fun readInto(byteBuffer: ByteBuffer): Int {
                    val size = buffer.readInto(byteBuffer)
                    internalRead.addAndGet(size.toLong())
                    return size
                }
            })
        }
        return result
    }

    override suspend fun asyncClose() {
        other.asyncClose()
    }
}
