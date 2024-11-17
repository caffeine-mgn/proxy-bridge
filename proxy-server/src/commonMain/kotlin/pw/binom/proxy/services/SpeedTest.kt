package pw.binom.proxy.services

import pw.binom.io.ByteBuffer
import pw.binom.io.nextBytes
import pw.binom.io.use
import pw.binom.io.useAsync
import pw.binom.services.VirtualChannelService
import pw.binom.strong.inject
import pw.binom.subchannel.WorkerChanelClient
import kotlin.time.Duration

class SpeedTest {
    private val virtualChannelService by inject<VirtualChannelService>()
    fun makeTest(time: Duration, channelCount: Int) {

    }

    suspend fun testSend(time: Duration) {
        virtualChannelService.createChannel().useAsync { channel ->
            WorkerChanelClient(channel).speedTest().testDownload(time)
        }
    }
}
