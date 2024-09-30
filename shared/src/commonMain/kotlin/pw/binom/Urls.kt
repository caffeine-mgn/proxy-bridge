package pw.binom

import pw.binom.url.toPathMask

object Urls {
    const val CONTROL = "/control"
    val TRANSPORT_TCP = "/tcp/{id}".toPathMask()
    val TRANSPORT_WS = "/raw/{id}".toPathMask()
    val TRANSPORT_LONG_POOLING_CLIENT_WRITE = "/lp/in/{id}".toPathMask()
    val TRANSPORT_LONG_POOLING_NODE_WRITE = "/lp/out/{id}".toPathMask()
}
