package pw.binom.proxy.properties

import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import org.koin.dsl.onClose
import pw.binom.TcpConnectProvider
import pw.binom.channel.ChannelSelector
import pw.binom.http.HttpProxy
import pw.binom.multiplexer.Multiplexer
import pw.binom.properties.Configuration
import pw.binom.properties.IncomeService
import pw.binom.properties.OutcomeService
import pw.binom.properties.OutcomeWrapperService
import pw.binom.proxy.Socks5Server
import pw.binom.proxy.service.SerialIncomeService
import pw.binom.proxy.services.PortForwardingService
import pw.binom.proxy.services.TcpConnectService
import pw.binom.proxy.services.TcpIncomeService
import pw.binom.proxy.services.TcpOutcomeService

object ConfigModule {
    fun createModule(config: Configuration) =
        module(createdAtStart = true) {
            single { ChannelSelector() }
            single { TcpConnectService(config.trafficRoute) }.bind(TcpConnectProvider::class)
            config.incomes.forEach { income ->
                when (income) {
                    is Configuration.Income.Com -> single(createdAtStart = true) {
                        SerialIncomeService(
                            serialName = income.port,
                            baudRate = income.speed,
                            channelSelector = get(),
                            idOdd = true,
                            name = ""
                        )
                    }
                        .onClose { it?.close() }
                        .binds(arrayOf(Multiplexer::class, IncomeService::class))

                    is Configuration.Income.Tcp -> single(createdAtStart = true) {
                        TcpIncomeService(
                            port = income.port,
                            host = income.bind,
                            channelSelector = get(),
                            selectorManager = get(),
                        )
                    }
                        .onClose { it?.close() }
                        .binds(arrayOf(IncomeService::class))
                }
            }

            config.outcomes?.forEach { (outcomeName, outcome) ->
                when (outcome) {
                    is Configuration.Outcome.Com -> single {
                        SerialIncomeService(
                            serialName = outcome.port,
                            baudRate = outcome.speed,
                            channelSelector = get(),
                            idOdd = false,
                            name = outcomeName
                        )
                    }
                        .onClose { it?.close() }
                        .binds(arrayOf(Multiplexer::class, OutcomeService::class))

                    is Configuration.Outcome.Tcp -> single {
                        TcpOutcomeService(
                            port = outcome.port,
                            host = outcome.host,
                            channelSelector = get(),
                            selectorManager = get(),
                            name = outcomeName,
                        )
                    }
                        .onClose { it?.close() }
                        .binds(arrayOf(Multiplexer::class, OutcomeService::class))

                    is Configuration.Outcome.Wrapper -> single {
                        OutcomeWrapperService(
                            name = outcomeName,
                            outcome = outcome.outcome,
                        )
                    }
                }
            }

            config.services.forEach { service ->
                when (service) {
                    is Configuration.Service.HttpProxy -> single(createdAtStart = true) {
                        HttpProxy(
                            port = service.port,
                            selector = get(),
                            onConnect = get(),
                        )
                    }.onClose { it?.close() }

                    is Configuration.Service.Socks5 -> single(createdAtStart = true) {
                        Socks5Server(
                            port = service.port,
                            selectorManager = get(),
                            authProvider = null,
                            onConnect = get(),
                            bind = service.bind,
                        )
                    }.onClose { it?.close() }

                    is Configuration.Service.TcpPortForward -> single(
                        createdAtStart = true,
                        qualifier = named("TcpPortForward ${service.bind}:${service.localPort}->${service.remoteHost}:${service.remotePort}")
                    ) {
                        PortForwardingService(
                            bindHost = service.bind,
                            bindPort = service.localPort,
                            remoteHost = service.remoteHost,
                            remotePort = service.remotePort,
                            tcpConnectProvider = get(),
                            selectorManager = get(),
                        )
                    }.onClose { it?.close() }
                }
            }
        }
}
