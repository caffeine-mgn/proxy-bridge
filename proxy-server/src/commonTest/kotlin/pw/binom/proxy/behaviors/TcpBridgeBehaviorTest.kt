package pw.binom.proxy.behaviors

import kotlinx.coroutines.delay
import pw.binom.*
import pw.binom.io.writeByteArray
import pw.binom.testing.Testing
import pw.binom.testing.shouldEquals
import pw.binom.testing.shouldNotNull
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class TcpBridgeBehaviorTest {
    @Test
    fun tcpFinishedTest() = Testing.async {
        val client = TestingGatewayClient()
        TcpBridgeBehavior.create(
            from = VirtualTransportChannel.create {
                readByteArray(10)
            },
            tcp = testChannel {
                writeByteArray(ByteArray(5))
            },
            client = client,
        ).run()
        delay(1.seconds)
        client.commandCount shouldEquals 1
        client.popCmd().also {
            it.resetChannel.shouldNotNull()
        }
    }

    @Test
    fun channelFinishedTest() = Testing.async {
        val client = TestingGatewayClient()
        var eof1 = false
        TcpBridgeBehavior.create(
            from = VirtualTransportChannel.create {
                writeByteArray(ByteArray(5))
            },
            tcp = testChannel {
//                try {
                    readByteArray(10)
//                } catch (e: EOFException) {
//                    eof1 = true
//                }
            },
            client = client,
        ).run()
        client.commandCount shouldEquals 1
        client.popCmd().also {
            it.resetChannel.shouldNotNull()
        }
        println("eof1=$eof1")
//        eof1.shouldBeTrue()
    }
}
