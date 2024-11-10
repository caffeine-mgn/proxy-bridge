package pw.binom

import pw.binom.io.AsyncChannel
import pw.binom.io.ByteArrayInput
import pw.binom.io.ByteArrayOutput
import pw.binom.io.ByteBuffer
import pw.binom.io.writeByteArray
import pw.binom.testing.Testing
import pw.binom.testing.shouldBeTrue
import pw.binom.testing.shouldContentEquals
import kotlin.test.Test

class ClosableAsyncChannelTest {
    fun gen(start: Byte, len: Int) = ByteArray(len) {
        (it + start).toByte()
    }

    @Test
    fun closeTest() = Testing.async {
        val data1 = gen(0, 30)//Random.nextBytes(30)
        val data2 = gen(30, 30)//Random.nextBytes(30)
        val inputBytes = byteArrayOf(ClosableAsyncChannel.DATA) + data1.size.toByteArray() + data1 +
                byteArrayOf(ClosableAsyncChannel.DATA) + data2.size.toByteArray() + data2 +
                byteArrayOf(ClosableAsyncChannel.CLOSED)
        val input = ByteArrayInput(inputBytes)
        val output = ByteArrayOutput()
        val channel = ClosableAsyncChannel(
            stream = AsyncChannel.create(
                input = input.asyncInput(),
                output = output.asyncOutput(),
            ),
            closeStream = {},
        )
        dispose { channel.asyncCloseAnyway() }
        test("read test spilled") {
            channel.readByteArray(10) shouldContentEquals data1.copyOfRange(0, 10)
            channel.readByteArray(10) shouldContentEquals data1.copyOfRange(10, 20)
            channel.readByteArray(10) shouldContentEquals data1.copyOfRange(20, 30)

            channel.readByteArray(10) shouldContentEquals data2.copyOfRange(0, 10)
            channel.readByteArray(10) shouldContentEquals data2.copyOfRange(10, 20)
            channel.readByteArray(10) shouldContentEquals data2.copyOfRange(20, 30)
            channel.read(ByteBuffer(1)).isClosed.shouldBeTrue()
            input.isEmpty.shouldBeTrue()
        }

        test("read test full 1") {
            channel.readByteArray(30) shouldContentEquals data1
            channel.readByteArray(30) shouldContentEquals data2
            channel.read(ByteBuffer(1)).isClosed.shouldBeTrue()
            input.isEmpty.shouldBeTrue()
        }

        test("read test full 2") {
            channel.readByteArray(60) shouldContentEquals data1 + data2
            channel.read(ByteBuffer(1)).isClosed.shouldBeTrue()
            input.isEmpty.shouldBeTrue()
        }

        test("write test spited") {
            val dataTotal = byteArrayOf(ClosableAsyncChannel.DATA) + data1.size.toByteArray() + data1
            channel.writeByteArray(data1)
            output.toByteArray() shouldContentEquals dataTotal
            channel.asyncClose()
            input.isEmpty.shouldBeTrue()
            output.toByteArray() shouldContentEquals dataTotal + byteArrayOf(ClosableAsyncChannel.CLOSED)
        }
        test("skip until close") {
            channel.asyncClose()
            input.isEmpty.shouldBeTrue()
            output.toByteArray() shouldContentEquals byteArrayOf(ClosableAsyncChannel.CLOSED)
        }
    }
}
