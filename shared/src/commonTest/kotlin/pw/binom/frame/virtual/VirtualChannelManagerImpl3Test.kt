package pw.binom.frame.virtual

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import pw.binom.*
import pw.binom.io.ByteBuffer
import pw.binom.io.wrap
import pw.binom.testing.shouldBeTrue
import pw.binom.testing.shouldContentEquals
import pw.binom.testing.shouldEquals
import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class VirtualChannelManagerImpl3Test : AbstractIT() {

    @Test
    fun newPackageTest() = testing {
        val manager = newVirtualChannelManager()
        launch { manager.new() }
        delay(1.seconds)
        val size = channel.pop().readInto(ByteBuffer(200))
        println("Size: $size")
    }

    @Test
    fun receiverUnknownChannel() = testing {
        val manager = newVirtualChannelManager()
        val channelId = ChannelId(100)
        val data = Random.nextBytes(100)
        closing(manager)

        channel.pushInput2 {
            VirtualManagerMessage.ChannelData(channelId, data.wrap()).write(it)
        }
        val incomeMessage = VirtualManagerMessage.read(channel)
        assertTrue(incomeMessage is VirtualManagerMessage.ChannelClosed)
        assertEquals(channelId, incomeMessage.channelId)
    }

    @Test
    fun sendTest() = testing {
        val manager = newVirtualChannelManager()
        val channelId = ChannelId(100)
        val data = Random.nextBytes(100)
        manager.send(channelId = channelId) { it.writeByteArray(data) }
        val dataMsg = VirtualManagerMessage.read(channel) as VirtualManagerMessage.ChannelData
        dataMsg.channelId shouldEquals channelId
        dataMsg.data.toByteArray() shouldContentEquals data
    }

    @Test
    fun receiverChannel() = testing {
        val manager = newVirtualChannelManager()
        val channelId = ChannelId(100)
        val data = Random.nextBytes(100)
        launch {
            delay(1.seconds)
            VirtualManagerMessage.ChannelData(channelId, data.wrap()).send(channel)
        }
        val bytes = manager.listen(channelId).readFrame { frame ->
            frame.readByteArray(data.size)
        }.ensureNotClosed()
        bytes shouldContentEquals data
    }

    @Test
    fun cancelReceiving() = testing {
        val manager = newVirtualChannelManager()
        val channelId = ChannelId(100)
        val listener = manager.listen(channelId)
        launch {
            delay(1.seconds)
            listener.asyncClose()
        }
        listener.readFrame { }.isClosed.shouldBeTrue()
        VirtualManagerMessage.ChannelData(channelId, Random.nextBytes(100).wrap()).send(channel)
        (VirtualManagerMessage.read(channel) as VirtualManagerMessage.ChannelClosed).channelId shouldEquals channelId
    }

    @Test
    fun closingReceiving() = testing {
        val manager = newVirtualChannelManager()
        val channelId = ChannelId(100)
        val listener = manager.listen(channelId)
        launch {
            delay(1.seconds)
            VirtualManagerMessage.ChannelClosed(channelId).send(channel)
        }
        listener.readFrame { }.isClosed.shouldBeTrue()
        VirtualManagerMessage.ChannelData(channelId, Random.nextBytes(100).wrap()).send(channel)
        (VirtualManagerMessage.read(channel) as VirtualManagerMessage.ChannelClosed).channelId shouldEquals channelId
    }

    @Test
    fun acceptTest() = testing {
        val manager = newVirtualChannelManager()
        val channelId = ChannelId(100)
        launch {
            delay(1.seconds)
            VirtualManagerMessage.NewChannel(channelId).send(channel)
        }
        manager.accept() shouldEquals channelId
    }

    @Test
    fun newTimeout() = testing {
        val manager = newVirtualChannelManager(serverMode = true)
        launch {
            withTimeout(1.seconds) { manager.new() }
//            delay(1.seconds)
//            val newMsg = VirtualManagerMessage.read(channel) as VirtualManagerMessage.NewChannel
//            VirtualManagerMessage.ChannelAccept(newMsg.channelId).send(channel)
        }
        delay(1.seconds)
        val channelId = (VirtualManagerMessage.read(channel) as VirtualManagerMessage.NewChannel).channelId
        delay(2.seconds)
        VirtualManagerMessage.ChannelAccept(channelId).send(channel)
        val closeChannelMsg = VirtualManagerMessage.read(channel) as VirtualManagerMessage.ChannelClosed
        closeChannelMsg.channelId shouldEquals channelId
    }

    @Test
    fun newServerMode() = testing {
        val manager = newVirtualChannelManager(serverMode = true)
        launch {
            delay(1.seconds)
            val newMsg = VirtualManagerMessage.read(channel) as VirtualManagerMessage.NewChannel
            VirtualManagerMessage.ChannelAccept(newMsg.channelId).send(channel)
        }
        val e = manager.new()
        e.raw shouldEquals 2.toShort()
    }

    @Test
    fun newClientMode() = testing {
        val manager = newVirtualChannelManager(serverMode = false)
        launch {
            delay(1.seconds)
            val newMsg = VirtualManagerMessage.read(channel) as VirtualManagerMessage.NewChannel
            VirtualManagerMessage.ChannelAccept(newMsg.channelId).send(channel)
        }
        val e = manager.new()
        e.raw shouldEquals 3.toShort()
    }
}

private suspend fun AbstractIT.TestContext.newVirtualChannelManager(serverMode: Boolean = false): VirtualChannelManagerImpl3 {
    val virtualManager = VirtualChannelManagerImpl3(
        source = channel,
        context = coroutineContext,
        serverMode = serverMode
    )
    closing(virtualManager)
    virtualManager.start()
    return virtualManager
}

private suspend fun VirtualManagerMessage.Companion.read(channel: TestingFrameChannel) =
    read(channel.pop(), AlwaysNewAllocator(channel.bufferSize.asInt))

private fun VirtualManagerMessage.send(channel: TestingFrameChannel) {
    channel.pushInput2 { this.write(it) }
}
