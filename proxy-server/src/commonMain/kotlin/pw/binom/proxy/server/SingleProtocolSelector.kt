package pw.binom.proxy.server

import pw.binom.io.httpClient.protocol.ConnectFactory2
import pw.binom.io.httpClient.protocol.ProtocolSelector
import pw.binom.url.URL

class SingleProtocolSelector(val factory: ConnectFactory2) : ProtocolSelector {
    override fun find(url: URL) = factory
}
