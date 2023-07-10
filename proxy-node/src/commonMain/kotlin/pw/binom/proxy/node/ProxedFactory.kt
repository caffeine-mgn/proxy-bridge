package pw.binom.proxy.node

import pw.binom.io.AsyncChannel
import pw.binom.io.httpClient.protocol.ConnectFactory2
import pw.binom.io.httpClient.protocol.HttpConnect
import pw.binom.io.httpClient.protocol.ProtocolSelector
import pw.binom.url.URL

class ProxedFactory(
    val protocolSelector: ProtocolSelector,
    val channelProvider: suspend (url: URL) -> AsyncChannel,
) : ConnectFactory2 {
    override fun createConnect(): HttpConnect =
        ProxedHttpConnect(
            channelProvider = channelProvider,
            protocolSelector = protocolSelector,
        )
}
