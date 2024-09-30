package pw.binom.gateway.services

import pw.binom.io.AsyncChannel
import pw.binom.io.http.BasicAuth
import pw.binom.io.http.BearerAuth
import pw.binom.io.socket.DomainSocketAddress
import pw.binom.network.NetworkManager
import pw.binom.network.tcpConnect
import pw.binom.gateway.properties.GatewayRuntimeProperties
import pw.binom.gateway.utils.tcpConnectViaHttpProxy
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.strong.inject
import pw.binom.url.isWildcardMatch

class TcpConnectionFactoryImpl : TcpConnectionFactory {
    private val runtimeProperties by inject<GatewayRuntimeProperties>()
    private val networkManager by inject<NetworkManager>()
    private val logger by Logger.ofThisOrGlobal
    override suspend fun connect(host: String, port: Int): AsyncChannel {
        val address = DomainSocketAddress(
            host = host,
            port = port,
        )

        val proxyConnect = runtimeProperties.proxy?.let PROXY@{ proxy ->
            proxy.noProxy?.let { noProxy ->
                if (noProxy.any { host.isWildcardMatch(it) }) {
                    return@PROXY null
                }
            }
            proxy.onlyFor?.let { onlyFor ->
                if (onlyFor.none { host.isWildcardMatch(it) }) {
                    return@PROXY null
                }
            }
            networkManager.tcpConnectViaHttpProxy(
                proxy = proxy.address.resolve(),
                address = address,
                auth = proxy.auth?.let { auth ->
                    when {
                        auth.basicAuth != null ->
                            BasicAuth(
                                login = auth.basicAuth.user,
                                password = auth.basicAuth.password
                            )

                        auth.bearerAuth != null -> BearerAuth(token = auth.bearerAuth.token)
                        else -> null
                    }
                },
                readBufferSize = proxy.bufferSize,
            )
        }
        if (proxyConnect != null) {
            logger.info("Connect to $host:$port using proxy")
            return proxyConnect
        }
        logger.info("Connect to $host:$port without proxy")
        return networkManager
            .tcpConnect(
                address = DomainSocketAddress(
                    host = host,
                    port = port
                ).resolve(),
            )
    }
}
