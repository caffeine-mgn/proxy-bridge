package pw.binom.proxy

fun interface ConnectProcessing {
    suspend fun connect(host: String, port: Int, context: ProxyingRawContext)
}
