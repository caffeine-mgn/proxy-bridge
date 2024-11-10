package pw.binom

import pw.binom.frame.virtual.VirtualChannel
import pw.binom.io.AsyncCloseable

interface VirtualChannelManager : AsyncCloseable {

    val channelCount: Int

    fun getOrCreateChannel(id: ChannelId): VirtualChannel
}
