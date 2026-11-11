package pw.binom.proxy.front

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import pw.binom.hid.Keyboard
import pw.binom.hid.TypingProfile
import pw.binom.hid.openHidDevice
import pw.binom.io.ioDispatcher
import pw.binom.serial.SerialConfig
import pw.binom.serial.openSerialPort
import kotlin.use
import kotlin.time.TimeSource

/** Один файл варианта бутстрапа. */
data class BootstrapFile(
    val name: String,
    val size: Long,
)

/**
 * Вариант бутстрапа — подкаталог в каталоге бутстрапа (python, java, csharp…).
 *
 * [launch] — команды запуска из `launch.txt`: печатаются в консоль удалённой
 * машины по кнопке, чтобы не набирать их руками.
 */
data class BootstrapVariant(
    val name: String,
    val files: List<BootstrapFile>,
    val launch: String?,
)

/**
 * Всё, что делает режим фронта на стороне Raspberry Pi:
 *
 * 1. отдаёт список вариантов бутстрапа и файлов внутри них;
 * 2. «печатает» файл или команды запуска в окно на удалённой машине (HID);
 * 3. отправляет саму программу по COM-порту уже запущенному бутстрапу.
 */
class BootstrapService(
    private val bootstrapRoot: String,
    private val settingsFile: String,
    private val keyboardDevice: String,
    private val profile: TypingProfile,
    private val serialPort: String,
    private val baudRate: Int,
    private val selfPath: String?,
    private val writeTimeoutMs: Int = 5000,
    private val exclude: List<String> = emptyList(),
) {
    private val logger = KotlinLogging.logger("BootstrapService")

    /** Имитация ввода не потокобезопасна: два «печатающих» запроса дадут кашу. */
    private val typingMutex = Mutex()

    companion object {
        /** Файл с командами запуска внутри каталога варианта. */
        const val LAUNCH_FILE = "launch.txt"

        /** Команды запуска пишутся построчно; после каждой — Enter. */
        private const val AFTER_COMMAND_DELAY_MS = 400
    }

    /** Файл, который отправляется кнопкой «отправить себя». */
    val selfFile: Path?
        get() = selfPath?.let { Path(it) }?.takeIf { SystemFileSystem.exists(it) }

    /** Варианты бутстрапа: подкаталоги [bootstrapRoot]. */
    fun variants(): List<BootstrapVariant> {
        val root = Path(bootstrapRoot)
        if (!SystemFileSystem.exists(root)) return emptyList()
        return SystemFileSystem.list(root)
            .mapNotNull { dir ->
                if (SystemFileSystem.metadataOrNull(dir)?.isDirectory != true) return@mapNotNull null
                val name = dir.name
                if (name.startsWith(".")) return@mapNotNull null
                val files = filesOf(dir)
                val launch = launchOf(dir)
                if (files.isEmpty() && launch == null) return@mapNotNull null
                BootstrapVariant(name = name, files = files, launch = launch)
            }
            .sortedBy { it.name }
    }

    private fun filesOf(dir: Path): List<BootstrapFile> =
        SystemFileSystem.list(dir)
            .mapNotNull { path ->
                if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) return@mapNotNull null
                val name = path.name
                // Документация и файл команд в списке «сохранить как» не нужны.
                if (name.startsWith("README", ignoreCase = true)) return@mapNotNull null
                if (name.equals(LAUNCH_FILE, ignoreCase = true)) return@mapNotNull null
                if (exclude.any { it.equals(name, ignoreCase = true) }) return@mapNotNull null
                BootstrapFile(name = name, size = SystemFileSystem.metadataOrNull(path)?.size ?: 0L)
            }
            // settings.txt первым: без него бутстрап не знает COM-порт.
            .sortedWith(compareBy({ it.name != settingsFile }, { it.name }))

    private fun launchOf(dir: Path): String? {
        val path = Path(dir, LAUNCH_FILE)
        if (!SystemFileSystem.exists(path)) return null
        return SystemFileSystem.source(path).buffered().use { it.readString() }.trim().ifEmpty { null }
    }

    /** Печатает файл варианта (по имени файла из [variants]). */
    suspend fun typeFile(variant: String, fileName: String): TypeResult = typingMutex.withLock {
        val file = variants().firstOrNull { it.name == variant }?.files?.firstOrNull { it.name == fileName }
            ?: return@withLock TypeResult(ok = false, message = "Файл $variant/$fileName не найден")
        val path = Path(Path(bootstrapRoot, variant), file.name)
        val text = withContext(ioDispatcher) {
            SystemFileSystem.source(path).buffered().use { it.readString() }
        }
        type(text, "$variant/${file.name}")
    }

    /** Печатает команды запуска варианта и нажимает Enter после каждой. */
    suspend fun typeLaunch(variant: String): TypeResult = typingMutex.withLock {
        val launch = variants().firstOrNull { it.name == variant }?.launch
            ?: return@withLock TypeResult(ok = false, message = "Нет $LAUNCH_FILE для варианта $variant")
        val started = TimeSource.Monotonic.markNow()
        val keyboard = openKeyboard()
        try {
            launch.lines().filter { it.isNotBlank() }.forEach { command ->
                keyboard.type(command)
                keyboard.press('\n')
                kotlinx.coroutines.delay(AFTER_COMMAND_DELAY_MS.toLong())
            }
        } catch (e: Throwable) {
            logger.error(e) { "Typing launch commands failed" }
            return@withLock TypeResult(ok = false, message = "Ошибка ввода: ${e.message}")
        } finally {
            runCatching { keyboard.close() }
        }
        val elapsed = started.elapsedNow().inWholeMilliseconds
        TypeResult(ok = true, message = "Команды запуска $variant напечатаны", elapsedMs = elapsed)
    }

    private suspend fun type(text: String, label: String): TypeResult {
        logger.info { "Typing $label (${text.length} chars) to $keyboardDevice" }
        val started = TimeSource.Monotonic.markNow()
        val keyboard = openKeyboard()
        try {
            keyboard.type(text)
        } catch (e: Throwable) {
            logger.error(e) { "Typing failed" }
            return TypeResult(ok = false, message = "Ошибка ввода: ${e.message}")
        } finally {
            runCatching { keyboard.close() }
        }
        val elapsedMs = started.elapsedNow().inWholeMilliseconds
        return TypeResult(
            ok = true,
            message = "Напечатано $label за ${elapsedMs / 1000} с",
            elapsedMs = elapsedMs,
        )
    }

    private suspend fun openKeyboard(): Keyboard = withContext(ioDispatcher) {
        Keyboard(openHidDevice(keyboardDevice), profile = profile)
    }

    /**
     * Отправляет файл бутстрапу по COM-порту (протокол — см. [BootstrapProtocol]).
     * Порт открывается на время передачи и сразу закрывается.
     */
    suspend fun sendFile(fileName: String, payload: ByteArray): SendResult = withContext(ioDispatcher) {
        val frame = BootstrapProtocol.frame(fileName, payload)
        logger.info { "Sending $fileName (${payload.size} bytes) to $serialPort at $baudRate" }
        runCatching {
            openSerialPort(
                name = serialPort,
                config = SerialConfig(
                    baudRate = baudRate,
                    readTimeoutMs = 3000,
                    writeTimeoutMs = writeTimeoutMs,
                ),
            ).use { port ->
                var offset = 0
                val chunk = 16 * 1024
                while (offset < frame.size) {
                    val size = minOf(chunk, frame.size - offset)
                    port.write(frame, offset, size)
                    offset += size
                }
                SendResult(
                    ok = true,
                    bytes = payload.size.toLong(),
                    ack = false,
                    message = "Передано $fileName (${payload.size} байт). " +
                        "Приём виден в консоли бутстрапа.",
                )
            }
        }.getOrElse {
            logger.error(it) { "Sending failed" }
            SendResult(
                ok = false,
                bytes = 0,
                ack = false,
                message = "Ошибка порта $serialPort: ${it.message}. " +
                    "Проверьте, что бутстрап запущен и COM-порт в его settings.txt тот же.",
            )
        }
    }

    /** Отправляет «себя» — файл, из которого запущена программа. */
    suspend fun sendSelf(): SendResult {
        val file = selfFile ?: return SendResult(
            ok = false,
            bytes = 0,
            ack = false,
            message = "Не задан файл для отправки (front.selfPath)",
        )
        val payload = withContext(ioDispatcher) {
            SystemFileSystem.source(file).buffered().use { it.readByteArray() }
        }
        return sendFile(file.name, payload)
    }

    data class TypeResult(val ok: Boolean, val message: String, val elapsedMs: Long = 0)

    data class SendResult(val ok: Boolean, val bytes: Long, val ack: Boolean, val message: String)
}
