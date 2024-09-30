package pw.binom

import pw.binom.io.*
import pw.binom.network.SocketClosedException
import pw.binom.proxy.readBinary
import pw.binom.proxy.testChannel
import pw.binom.proxy.writeBinary
import pw.binom.testing.Testing
import pw.binom.testing.shouldContentEquals
import pw.binom.testing.shouldEquals
import kotlin.random.Random
import kotlin.test.Test

class StreamBridgeTest {
    @Test
    fun simpleCopyTest() = Testing.async {
        val inputData = Random.nextBytes(1024 * 1024 * 2)
        var len = 0uL
        ByteArrayInput(inputData).use { input ->
            val output = ByteArrayOutput()
            val copyResult = ByteBuffer(DEFAULT_BUFFER_SIZE).use { buffer ->
                StreamBridge.copy(
                    left = input.asyncInput(),
                    right = output.asyncOutput(),
                    buffer = buffer,
                    sizeProvider = { len = it },
                )
            }
            output.toByteArray() shouldContentEquals inputData
            len shouldEquals inputData.size.toULong()
            copyResult shouldEquals StreamBridge.ReasonForStopping.LEFT
        }
    }

    @Test
    fun outputClosedTest() = Testing.async {
        val inputData = Random.nextBytes(1024 * 1024 * 2)
        var len = 0uL
        val maxLen = DEFAULT_BUFFER_SIZE * 2
        ByteArrayInput(inputData).use { input ->
            val output = OutputWithLimit(maxLen)
            val copyResult = ByteBuffer(DEFAULT_BUFFER_SIZE).use { buffer ->
                StreamBridge.copy(
                    left = input.asyncInput(),
                    right = output.asyncOutput(),
                    buffer = buffer,
                    sizeProvider = { len = it },
                )
            }
            output.toByteArray() shouldContentEquals inputData.copyOf(maxLen)
            len shouldEquals maxLen.toULong()
            copyResult shouldEquals StreamBridge.ReasonForStopping.RIGHT
        }
    }

    @Test
    fun testing() = Testing.async {
        val inputData1 = Random.nextBytes(100)
        val inputData2 = Random.nextBytes(200)
        val inputData3 = Random.nextBytes(300)
        var left: AsyncChannel? = null
        var right: AsyncChannel? = null
        test("multi-direction coping") {
            left = testChannel {
                writeBinary(inputData1)
                readBinary(inputData1.size) shouldContentEquals inputData1
                writeBinary(inputData2)
                readBinary(inputData2.size) shouldContentEquals inputData2
                writeBinary(inputData3)
                readBinary(inputData3.size) shouldContentEquals inputData3
            }
            right = testChannel {
                readBinary(inputData1.size) shouldContentEquals inputData1
                writeBinary(inputData1)
                readBinary(inputData2.size) shouldContentEquals inputData2
                writeBinary(inputData2)
                readBinary(inputData3.size) shouldContentEquals inputData3
                writeBinary(inputData3)
            }
        }


        StreamBridge.sync(
            left = left!!,
            right = right!!,
            bufferSize = 100
        )
    }

    class OutputWithLimit(val maxLen: Int) : ByteArrayOutput() {
        override fun write(data: ByteBuffer): DataTransferSize {
            if (this.size >= maxLen) {
                throw SocketClosedException()
            }
            return super.write(data)
        }
    }
}
