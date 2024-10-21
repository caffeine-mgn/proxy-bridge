package pw.binom.gateway.behaviors

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pw.binom.*
import pw.binom.gateway.Context
import pw.binom.io.writeByteArray
import pw.binom.network.SocketClosedException
import pw.binom.proxy.ChannelId
import pw.binom.testing.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class ConnectTcpBehaviorTest {
    /**
     * Тестирование не известного имени хоста
     */
    @Test
    fun unknownHostTest() = Testing.async {
        val hostName = "sdfdsf333"
        Context.use {
            transport {}
            connect(host = hostName, port = 1).shouldNull()
            client.eventCount shouldEquals 1
            client.popEvent().also {
                it.proxyError.shouldNotNull()
                    .msg shouldEquals "Unknown Host $hostName"
            }
        }
    }

    /**
     * Хост известен, но порт закрыт
     */
    @Test
    fun connectionRefusedTest() = Testing.async {
        Context.use {
            transport { }
            connect(port = 1).shouldNull()
            client.eventCount shouldEquals 1
            client.popEvent().also {
                it.proxyError.shouldNotNull()
                    .msg
                    .shouldNotNull()
                    .let { "CONNECTION_REFUSED" in it }.shouldBeTrue()
            }
        }
    }

    @Test
    fun emptyChannel() = Testing.async {
        var ok = false
        Context.use {
            transport { writeByteArray(byteArrayOf(1, 2, 3, 4, 5)) }
            tcpOneConnect {
                try {
                    readByteArray(10)
                } catch (e: SocketClosedException) {
                    ok = true
                }
            }
            val b = connect().shouldNotNull()
            b.run()
            delay(1.seconds)
            ok.shouldBeTrue()
            client.eventCount shouldEquals 0
        }
    }

    @Test
    fun closeNowTest() = Testing.async {
        Context.use {
            val channelId = ChannelId(Random.nextInt().toString())
            transport(channelId) { readByteArray(10) }
            test("send empty") {
                tcpOneConnect { }
            }
            test("send 5 bytes") {
                tcpOneConnect { writeByteArray(ByteArray(5)) }
            }
            connect().shouldNotNull().run()
            client.eventCount shouldEquals 1
            client.popEvent().also {
                it.chanelEof.shouldNotNull()
                    .channelId shouldEquals channelId
            }
        }
    }

    @Test
    fun breakConnection() = Testing.async {
        Context.use {
            var socketClosed = false
            tcpOneConnect {
                try {
                    println("Try read 10 bytes")
                    readByteArray(10)
                } catch (e: SocketClosedException) {
                    println("Socket closed")
                    socketClosed = true
                }
            }
            transport { readByteArray(10) }
            transportChannel!!.isClosed
            val e = connect().shouldNotNull()
            GlobalScope.launch {
                delay(2.seconds)
                println("Send interrupted...")
                e.asyncClose()
            }
            val t = measureTime {
                e.run()
            }
            println("t====$t")
            delay(1.seconds)
            socketClosed.shouldBeTrue()
            println("transportChannel!!.isClosed=${transportChannel!!.isClosed}")
            client.eventCount shouldEquals 0
        }
    }
}


