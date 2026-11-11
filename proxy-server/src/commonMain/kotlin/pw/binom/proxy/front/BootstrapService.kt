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
import kotlin.time.TimeSource
import kotlin.use

/**
 * Всё, что делает режим фронта на стороне Raspberry Pi:
 *
 * 1. отдаёт список файлов бутстрапа;
 * 2. «печатает» выбранный файл в окно блокнота на удалённой машине (HID);
 * 3. отправляет сам бинарник по COM-порту уже запущенному бутстрапу.
 */
class BootstrapService(
    private val bootstrapDir: String,
    private val settingsFile: String,
    private val exclude: List<String> = emptyList(),
    private val keyboardDevice: String,
    private val profile: TypingProfile,
    private val serialPort: String,
    private val baudRate: Int,
    private val selfPath: String?,
) {
    private val logger = KotlinLogging.logger("BootstrapService")

    /** Имитация ввода не потокобезопасна: два «печатающих» запроса дадут кашу. */
    private val typingMutex = Mutex()

    /** Файл, который отправляется кнопкой «отправить себя». */
    val selfFile: Path?
        get() = selfPath?.let { Path(it) }?.takeIf { SystemFileSystem.exists(it) }

    fun files(): List<BootstrapFile> {
        val dir = Path(bootstrapDir)
        if (!SystemFileSystem.exists(dir)) return emptyList()
        return SystemFileSystem.list(dir)
            .mapNotNull { path ->
                if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) return@mapNotNull null
                val name = path.name
                // Документация в бутстрап не входит: её незачем печатать.
                if (name.startsWith("README", ignoreCase = true)) return@mapNotNull null
                if (exclude.any { it.equals(name, ignoreCase = true) }) return@mapNotNull null
                val size = SystemFileSystem.metadataOrNull(path)?.size ?: 0L
                name to size
            }
            // settings.txt идёт первым: без него бутстрап не знает COM-порт.
            .sortedWith(compareBy({ it.first != settingsFile }, { it.first }))
            .mapIndexed { index, (name, size) -> BootstrapFile(id = index + 1, name = name, size = size) }
    }

    /** Печатает файл бутстрапа с указанным id. */
    suspend fun typeFile(id: Int): TypeResult {
        val file = files().firstOrNull { it.id == id }
            ?: return TypeResult(ok = false, message = "Файл #$id не найден")
        return typeFileByName(file.name)
    }

    /** Печатает файл настроек бутстрапа (settings.txt). */
    suspend fun typeSettings(): TypeResult = typeFileByName(settingsFile)

    private suspend fun typeFileByName(name: String): TypeResult = typingMutex.withLock {
        val path = Path(bootstrapDir, name)
        if (!SystemFileSystem.exists(path)) {
            return@withLock TypeResult(ok = false, message = "Файл $name не найден")
        }
        val text = withContext(ioDispatcher) {
            SystemFileSystem.source(path).buffered().use { it.readString() }
        }
        logger.info { "Typing bootstrap file $name (${text.length} chars) to $keyboardDevice" }
        val started = TimeSource.Monotonic.markNow()
        val keyboard = withContext(ioDispatcher) { Keyboard(openHidDevice(keyboardDevice), profile = profile) }
        try {
            keyboard.type(text)
        } catch (e: Throwable) {
            logger.error(e) { "Typing failed" }
            return@withLock TypeResult(ok = false, message = "Ошибка ввода: ${e.message}")
        } finally {
            runCatching { keyboard.close() }
        }
        val elapsedMs = started.elapsedNow().inWholeMilliseconds
        TypeResult(
            ok = true,
            message = "Напечатано $name за ${elapsedMs / 1000} с",
            elapsedMs = elapsedMs,
        )
    }

    /**
     * Отправляет файл бутстрапу по COM-порту и ждёт подтверждения.
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
                    writeTimeoutMs = 10_000,
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
            SendResult(ok = false, bytes = 0, ack = false, message = "Ошибка порта: ${it.message}")
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
