package pw.binom.proxy

import com.charleskorn.kaml.Yaml
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.readText
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import pw.binom.proxy.channel.TcpConnectChannel
import pw.binom.proxy.io.SelectorManagerKoinModule
import pw.binom.proxy.front.FrontServer
import pw.binom.properties.Configuration
import pw.binom.proxy.properties.ConfigModule
import kotlin.use

private val logger = KotlinLogging.logger("GLOBAL")

/** Поднятый фронт (null — если работает обычный режим). */
var frontServer: FrontServer? = null
    private set

/**
 * Загружает YAML-конфигурацию из рабочей директории. Возвращает null, если
 * файла нет (поведение историческое: молча завершиться).
 */
fun loadConfiguration(fileName: String = "config.yaml"): Configuration? {
    val configFile = Path(fileName)
    if (!SystemFileSystem.exists(configFile)) {
        println("Config file missing: $fileName")
        return null
    }
    return SystemFileSystem.source(configFile).buffered().use {
        Yaml.default.decodeFromString(Configuration.serializer(), it.readText())
    }
}

/**
 * Поднимает все сервисы из конфигурации через Koin.
 *
 * Если включён режим фронта ([Configuration.front]), обычные сервисы НЕ
 * поднимаются: COM-порт и USB-клавиатура нужны бутстрапу, поэтому режимы
 * взаимоисключающие. В этом случае возвращается null, а фронт остаётся жить
 * отдельным сервером.
 *
 * Возвращает запущенный [KoinApplication]; вызывающий код должен удерживать
 * процесс живым.
 */
fun startProxy(config: Configuration, selfPath: String? = null): KoinApplication? {
    println("config:\n${Yaml.default.encodeToString(Configuration.serializer(), config)}")
    val front = config.front
    if (front != null && front.enabled) {
        logger.info { "FRONT MODE: proxy services are disabled" }
        println("FRONT MODE: веб-интерфейс на ${front.bind}:${front.port}, проксирование выключено")
        if (config.services.isNotEmpty() || config.incomes.isNotEmpty()) {
            println("FRONT MODE: services/incomes из конфигурации проигнорированы")
        }
        frontServer = FrontServer.create(front, selfPath)
        return null
    }
    logger.info { "STARTUP" }
    return startKoin {
        modules(
            ConfigModule.createModule(config),
            SelectorManagerKoinModule,
            module { single { TcpConnectChannel(get(), get(), get()) } },
            module {
                single { ConnectProcessingImpl(get()) }.bind(ConnectProcessing::class)
            },
            TcpConnectChannel.module,
        )
    }
}
