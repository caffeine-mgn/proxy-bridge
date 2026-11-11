package pw.binom.properties

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class Configuration(
    /**
     * Список проксей, которые нужно поднять и принимать подключение
     */
    val services: List<Service> = emptyList(),

    /**
     * Список подключений, для ожидания подключения
     */
    val incomes: List<Income> = emptyList(),

    /**
     * Список подключений для перенаправления запроса
     */
    val outcomes: Map<String, Outcome>? = null,

    /**
     * Настройка имен
     */
    val hostConfig: List<HostConfig> = emptyList(),
    val tcpForwarding: List<SocketForwarding> = emptyList(),
    val trafficRoute: List<TrafficRoute> = emptyList(),

    /**
     * Файловые системы
     */
    val fileSystems: Map<String, FileSystemConfig> = emptyMap(),

    /**
     * Режим «фронт»: веб-интерфейс для первичной доставки программы на
     * удалённую машину (имитация ввода файлов бутстрапа + передача бинарника
     * по COM-порту).
     *
     * Режим ВЗАИМОИСКЛЮЧАЮЩИЙ с обычной работой: когда он включён, сервисы из
     * [services]/[incomes]/[outcomes] не поднимаются, потому что COM-порт и
     * клавиатура нужны самому бутстрапу.
     */
    val front: Front? = null,
) {

    @Serializable
    data class SocketForwarding(
        val localPort: Int,
        val remoteHost: String,
        val remotePort: Int,
    )

    @Serializable
    sealed interface Outcome {
        @Serializable
        @SerialName("com")
        data class Com(val port: String, val speed: Int = 115200) : Outcome
        data class Tcp(val host: String = "0.0.0.0", val port: Int) : Outcome
        data class Wrapper(val outcome: String) : Outcome
    }

    @Serializable
    sealed interface Income {
        @Serializable
        @SerialName("com")
        data class Com(val port: String, val speed: Int = 115200) : Income

        @Serializable
        @SerialName("tcp")
        data class Tcp(val bind: String = "0.0.0.0", val port: Int) : Income
//
//        @Serializable
//        @SerialName("ws")
//        data class WebSocket(val bind: String = "0.0.0.0", val port: String, val endpoint: String = "/ws") : Income
    }

    @Serializable
    data class Auth(val username: String, val password: String)

    @Serializable
    sealed interface Egress {
        @Serializable
        @SerialName("direct")
        object Direct : Egress

        @Serializable
        @SerialName("http-proxy")
        class HttpProxy(val host: String, val port: Int, val auth: Auth? = null) : Egress

        @SerialName("outcome")
        @Serializable
        class Outcome(val outcome: String) : Egress
    }

    @Serializable
    sealed interface TrafficRoute {
        val rule: Egress

        @Serializable
        @SerialName("by-domain")
        class ByDomain(val host: String, override val rule: Egress) : TrafficRoute

        @Serializable
        @SerialName("always")
        class Always(override val rule: Egress) : TrafficRoute
    }

    @Serializable
    enum class FilterMode {
        @SerialName("include")
        INCLUDE,

        @SerialName("exclude")
        EXCLUDE,
    }

    @Serializable
    data class HostConfig(
        val hosts: Set<String>,
        val filterMode: FilterMode,
    )

    @Serializable
    sealed interface FileSystemConfig {
        @Serializable
        @SerialName("local")
        data class Local(val root: String) : FileSystemConfig

        @Serializable
        @SerialName("remote")
        data class Remote(val outcome: String, val fs: String) : FileSystemConfig

        @Serializable
        @SerialName("merged")
        data class Merged(val layers: List<Layer>) : FileSystemConfig

        @Serializable
        data class Layer(val path: String, val fs: String)
    }

    @Serializable
    data class Front(
        /** Включён ли режим фронта. Выключен — работает обычная логика программы. */
        val enabled: Boolean = true,

        /** Адрес веб-сервера фронта. */
        val bind: String = "0.0.0.0",
        val port: Int = 8900,

        /**
         * Каталог с файлами бутстрапа. Каждый файл отдаётся в интерфейсе
         * отдельной кнопкой: пользователь нажимает, ввод «печатается» в окно
         * блокнота на удалённой машине, а он сохраняет файл под показанным
         * именем.
         */
        val bootstrapDir: String = "bootstrap",

        /** Имя файла настроек бутстрапа внутри [bootstrapDir]. */
        val settingsFile: String = "settings.txt",

        /**
         * Что НЕ показывать в списке печатаемых файлов (например, сборочные
         * файлы, если на удалённой машине нет Gradle). README* отсекается всегда.
         */
        val bootstrapExclude: List<String> = emptyList(),

        /**
         * Что отправлять кнопкой «отправить себя». Если не задано — берётся
         * файл, из которого запущена программа (JVM-джар), а если и это
         * недоступно, отправка отключается.
         */
        val selfPath: String? = null,

        /** COM-устройство для передачи файла на удалённую машину. */
        val serialPort: String = "/dev/ttyGS0",
        val baudRate: Int = 115200,

        /** Сколько ждать записи в порт, прежде чем сообщить, что нас не читают. */
        val writeTimeoutMs: Int = 5000,

        /** HID-клавиатура USB-гаджета. */
        val keyboardDevice: String = "/dev/hidg0",

        /** Профиль имитации ввода (паузы в миллисекундах). */
        val charDelayMs: Int = 15,
        val jitterMs: Int = 10,
        val holdMs: Int = 8,

        /**
         * Настройка USB-гаджета при старте: приложение само проверяет, что
         * устройство прикидывается COM-портом и клавиатурой, и создаёт
         * конфигурацию, если её нет. Требует root (configfs).
         */
        val usbGadget: UsbGadget = UsbGadget(),
    ) {
        @Serializable
        data class UsbGadget(
            /** Проверять и при необходимости настраивать гаджет при старте. */
            val configure: Boolean = true,
            /** Имя гаджета в configfs. */
            val name: String = "g1",
            /** UDC; null — первый доступный в /sys/class/udc. */
            val udc: String? = null,
        )
    }

    @Serializable
    sealed interface Service {
        @Serializable
        @SerialName("http-proxy")
        data class HttpProxy(val bind: String = "0.0.0.0", val port: Int = 8080) : Service

        @Serializable
        @SerialName("socks5-proxy")
        data class Socks5(val bind: String = "0.0.0.0", val port: Int = 1080) : Service

        @Serializable
        @SerialName("tcp-port-forward")
        data class TcpPortForward(
            val bind: String = "0.0.0.0",
            val localPort: Int = 1080,
            val remotePort: Int,
            val remoteHost: String,
        ) : Service

        @Serializable
        @SerialName("webdav")
        data class WebDav(
            val bind: String = "0.0.0.0",
            val port: Int = 8075,
            val basePath: String = "/dav",
            val fs: String,
        ) : Service
    }
}
