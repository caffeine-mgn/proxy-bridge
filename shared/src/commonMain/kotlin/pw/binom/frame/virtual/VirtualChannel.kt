package pw.binom.frame.virtual

import pw.binom.ChannelId
import pw.binom.frame.FrameChannel

interface VirtualChannel : FrameChannel {
    val id: ChannelId
}
