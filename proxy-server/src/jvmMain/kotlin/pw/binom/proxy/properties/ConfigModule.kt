package pw.binom.proxy.properties

import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import org.koin.dsl.onClose
import pw.binom.proxy.services.TcpConnectProvider
import pw.binom.proxy.services.ChannelSelectorService
import pw.binom.proxy.http.HttpProxy
import pw.binom.multiplexer.Multiplexer
import pw.binom.properties.Configuration
import pw.binom.proxy.services.IncomeService
import pw.binom.properties.OutcomeService
import pw.binom.proxy.services.OutcomeWrapperService
import pw.binom.proxy.socks5.Socks5Server
import pw.binom.proxy.service.SerialIncomeService
import pw.binom.proxy.services.PortForwardingService
import pw.binom.proxy.services.TcpConnectService
import pw.binom.proxy.services.TcpIncomeService
import pw.binom.proxy.services.TcpOutcomeService
import pw.binom.proxy.channel.FileChannel
import pw.binom.proxy.channel.ChannelHandler
import pw.binom.proxy.webdav.RemoteWebDavFileSystem
import pw.binom.webdav.fs.WebDavFileSystem
import pw.binom.webdav.fs.local.LocalFileSystem
import pw.binom.webdav.fs.mounted.MountedFileSystem
import pw.binom.proxy.WebDavServer
import kotlinx.io.files.Path

object ConfigModule {
    fun createModule(config: Configuration) =
        module(createdAtStart = true) {
            single { ChannelSelectorService() }
            single { TcpConnectService(config.trafficRoute) }.bind(TcpConnectProvider::class)

            config.incomes.forEach { income ->
                when (income) {
                    is Configuration.Income.Com -> single(createdAtStart = true) {
                        SerialIncomeService(
                            serialName = income.port,
                            baudRate = income.speed,
                            channelSelectorService = get(),
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
                            channelSelectorService = get(),
                            selectorManager = get(),
                        )
                    }
                        .onClose { it?.close() }
                        .binds(arrayOf(IncomeService::class))
                }
            }

            config.outcomes?.forEach { (outcomeName, outcome) ->
                when (outcome) {
                    is Configuration.Outcome.Com -> single(qualifier = named(outcomeName)) {
                        SerialIncomeService(
                            serialName = outcome.port,
                            baudRate = outcome.speed,
                            channelSelectorService = get(),
                            idOdd = false,
                            name = outcomeName
                        )
                    }
                        .onClose { it?.close() }
                        .binds(arrayOf(Multiplexer::class, OutcomeService::class))

                    is Configuration.Outcome.Tcp -> single(qualifier = named(outcomeName)) {
                        TcpOutcomeService(
                            port = outcome.port,
                            host = outcome.host,
                            channelSelectorService = get(),
                            selectorManager = get(),
                            name = outcomeName,
                        )
                    }
                        .onClose { it?.close() }
                        .binds(arrayOf(Multiplexer::class, OutcomeService::class))

                    is Configuration.Outcome.Wrapper -> single(qualifier = named(outcomeName)) {
                        OutcomeWrapperService(
                            name = outcomeName,
                            outcome = outcome.outcome,
                        )
                    }
                }
            }

            // === Файловые системы ===

            val fsNames = mutableListOf<String>()
            config.fileSystems.forEach { (fsName, fsConfig) ->
                fsNames.add(fsName)
                when (fsConfig) {
                    is Configuration.FileSystemConfig.Local -> single(qualifier = named(fsName)) {
                        LocalFileSystem(Path(fsConfig.root))
                    }.bind(WebDavFileSystem::class)

                    is Configuration.FileSystemConfig.Remote -> single(qualifier = named(fsName)) {
                        RemoteWebDavFileSystem(
                            outcome = get(named(fsConfig.outcome)),
                            fileChannel = get(),
                            name = fsConfig.fs,
                        )
                    }.bind(WebDavFileSystem::class)

                    is Configuration.FileSystemConfig.Merged -> single(qualifier = named(fsName)) {
                        val mounted = MountedFileSystem()
                        for (layer in fsConfig.layers) {
                            val layerFs = get<WebDavFileSystem>(named(layer.fs))
                            mounted.mount(layer.path, layerFs)
                        }
                        mounted
                    }.bind(WebDavFileSystem::class)
                }
            }

            // FileChannel с fsResolver
            single {
                val resolver: (String) -> WebDavFileSystem? = { name ->
                    if (name in fsNames) get(named(name)) else null
                }
                FileChannel(resolver)
            } bind ChannelHandler::class

            // === Services ===

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

                    is Configuration.Service.WebDav -> single(createdAtStart = true) {
                        WebDavServer(
                            port = service.port,
                            bind = service.bind,
                            basePath = service.basePath,
                            fileSystem = get(named(service.fs)),
                        )
                    }.onClose { it?.close() }
                }
            }
        }
}
