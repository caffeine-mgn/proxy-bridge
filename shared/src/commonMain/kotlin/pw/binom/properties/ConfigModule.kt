package pw.binom.properties

import org.koin.dsl.binds
import org.koin.dsl.module
import org.koin.dsl.onClose
import pw.binom.channel.ChannelSelector
import pw.binom.http.HttpProxy
import pw.binom.multiplexer.Multiplexer
import pw.binom.proxy.Socks5Server

object ConfigModule {
    fun createModule(config: Configuration) =
        module(createdAtStart = true) {
            single { ChannelSelector() }
            when (config.income) {
                is Configuration.Income.Com -> single {
                    SerialIncomeService(
                        serialName = config.income.port,
                        baudRate = config.income.speed,
                        channelSelector = get(),
                        idOdd = true,
                    )
                }
                    .onClose { it?.close() }
                    .binds(arrayOf(Multiplexer::class, IncomeService::class))

                null -> {}
            }
            when (config.outcome) {
                is Configuration.Income.Com -> single {
                    SerialIncomeService(
                        serialName = config.outcome.port,
                        baudRate = config.outcome.speed,
                        channelSelector = get(),
                        idOdd = false,
                    )
                }
                    .onClose { it?.close() }
                    .binds(arrayOf(Multiplexer::class, IncomeService::class))

                null -> {}
            }
            config.proxies.forEach { proxy ->
                when (proxy.type) {
                    Configuration.ProxyType.HTTP -> single {
                        HttpProxy(
                            port = proxy.port,
                            selector = get(),
                            onConnect = get(),
                        )
                    }.onClose { it?.close() }

                    Configuration.ProxyType.SOCKS5 -> single {
                        Socks5Server(
                            port = proxy.port,
                            selectorManager = get(),
                            authProvider = null,
                            onConnect = get(),
                            bind = proxy.bind,
                        )
                    }.onClose { it?.close() }
                }
            }
        }
}
