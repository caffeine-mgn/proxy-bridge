package pw.binom.services

import io.ktor.network.selector.SelectorManager
import org.koin.core.component.KoinComponent
import pw.binom.TcpConnectProvider
import pw.binom.properties.Configuration
import pw.binom.properties.OutcomeService

class TcpConnectService(val routes: List<Configuration.TrafficRoute>) : KoinComponent, TcpConnectProvider {
    private val outcomes by lazy { getKoin().getAll<OutcomeService>().associateBy { it.name } }
    private val selector = getKoin().get<SelectorManager>()

    private val provider = run {
        val rules = routes.map {
            val provider = when (val rule = it.rule) {
                Configuration.Egress.Direct -> TcpConnectProvider.Direct(selector)
                is Configuration.Egress.HttpProxy -> {
                    val auth = rule.auth?.let { TcpConnectProvider.Auth(it.username, it.password) }
                    TcpConnectProvider.HttpProxy(
                        selector = selector,
                        host = rule.host,
                        port = rule.port,
                        auth = auth
                    )
                }

                is Configuration.Egress.Outcome -> {
                    val outcome =
                        outcomes[rule.outcome] ?: throw IllegalStateException("Outcome ${rule.outcome} not found")
                    TcpConnectProvider.UsingOutcome(outcome)
                }
            }

            when (it) {
                is Configuration.TrafficRoute.Always -> TcpConnectProvider.ByRule.Rule.Always(provider)
                is Configuration.TrafficRoute.ByDomain -> TcpConnectProvider.ByRule.Rule.ByDomain(
                    host = it.host,
                    provider = provider
                )
            }
        }

        TcpConnectProvider.ByRule(rules)
    }

    override suspend fun connect(host: String, port: Int): TcpConnectProvider.ConnectResult =
        provider.connect(host, port)
}
