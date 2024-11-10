package pw.binom.communicate.tcp

import kotlinx.serialization.Serializable

@Serializable
@Deprecated(message = "Not use it")
class TcpClientData(
    val host: String,
    val port: Short,
)
