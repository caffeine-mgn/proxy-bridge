package pw.binom.proxy.front

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pw.binom.hid.TypingProfile
import pw.binom.properties.Configuration
import pw.binom.usb.gadget.GadgetStatus
import pw.binom.usb.gadget.UsbGadgetManager

/**
 * Режим «фронт»: веб-интерфейс для первичной доставки программы на удалённую
 * машину.
 *
 * Сервис заменяет обычную работу программы целиком: пока он включён, COM-порт и
 * USB-клавиатура заняты бутстрапом, туннель не поднимается.
 */
class FrontServer(
    bind: String,
    port: Int,
    private val bootstrap: BootstrapService,
    /** Менеджер USB-гаджета; null — настройка гаджета недоступна. */
    private val gadget: UsbGadgetManager? = null,
) : AutoCloseable {
    companion object {
        fun create(
            front: Configuration.Front,
            selfPath: String?,
            gadget: UsbGadgetManager? = null,
        ): FrontServer {
            val service = BootstrapService(
                bootstrapRoot = front.bootstrapDir,
                settingsFile = front.settingsFile,
                keyboardDevice = front.keyboardDevice,
                profile = TypingProfile(
                    charDelayMs = front.charDelayMs,
                    jitterMs = front.jitterMs,
                    holdMs = front.holdMs,
                ),
                serialPort = front.serialPort,
                baudRate = front.baudRate,
                selfPath = front.selfPath ?: selfPath,
                writeTimeoutMs = front.writeTimeoutMs,
                exclude = front.bootstrapExclude,
            )
            return FrontServer(front.bind, front.port, service, gadget)
        }

        private val json = Json { encodeDefaults = true }
    }

    private val logger = KotlinLogging.logger("FrontServer")

    private val server = embeddedServer(CIO, port = port, host = bind) {
        frontModule()
    }

    init {
        logger.info {
            "Starting front server on $bind:$port " +
                "(bootstrap variants: ${bootstrap.variants().joinToString { it.name }})"
        }
        server.start(wait = false)
    }

    override fun close() {
        server.stop(1000, 3000)
    }

    private fun Application.frontModule() {
        routing {
            get("/") {
                call.respondText(FrontPage.html, ContentType.Text.Html)
            }

            get("/api/status") {
                call.respondJson(
                    StatusResponse(
                        mode = "front",
                        variants = bootstrap.variants().size,
                        selfFile = bootstrap.selfFile?.name,
                    ),
                )
            }

            /** Варианты бутстрапа с файлами и командами запуска. */
            get("/api/bootstrap") {
                call.respondJson(
                    bootstrap.variants().map { variant ->
                        VariantResponse(
                            name = variant.name,
                            launches = variant.launches.map { LaunchResponse(it.name, it.commands) },
                            files = variant.files.map { FileResponse(it.name, it.size) },
                        )
                    },
                )
            }

            post("/api/bootstrap/type") {
                val request = json.decodeFromString<TypeFileRequest>(call.receiveText())
                val result = bootstrap.typeFile(request.variant, request.file)
                call.respondJson(ActionResponse(result.ok, result.message))
            }

            post("/api/bootstrap/launch") {
                val request = json.decodeFromString<LaunchRequest>(call.receiveText())
                val result = bootstrap.typeLaunch(request.variant, request.launch)
                call.respondJson(ActionResponse(result.ok, result.message))
            }

            /** Состояние USB-гаджета: прикидывается ли малина COM + клавиатурой. */
            get("/api/gadget") {
                call.respondJson(gadgetStatus(gadget?.status()))
            }

            /** Настроить гаджет (создать конфигурацию и привязать к UDC). */
            post("/api/gadget/setup") {
                val manager = gadget
                if (manager == null) {
                    call.respondJson(
                        GadgetResponse(
                            ok = false,
                            message = "Настройка гаджета в этом запуске недоступна " +
                                "(front.usbGadget.configure = false)",
                        ),
                    )
                } else {
                    val status = manager.ensureConfigured()
                    val response = gadgetStatus(status)
                    call.respondJson(
                        response.copy(
                            ok = status.ready,
                            message = if (status.ready) {
                                "Гаджет настроен: ${status.serialDevice} + ${status.keyboardDevice}"
                            } else {
                                "Не удалось настроить гаджет"
                            },
                        ),
                    )
                }
            }

            post("/api/bootstrap/send-self") {
                val result = bootstrap.sendSelf()
                call.respondJson(
                    ActionResponse(
                        ok = result.ok,
                        message = result.message,
                        bytes = result.bytes,
                    ),
                )
            }
        }
    }

    private suspend inline fun <reified T> ApplicationCall.respondJson(body: T) {
        respondText(json.encodeToString(body), ContentType.Application.Json)
    }

    private suspend fun ApplicationCall.respondJson(body: List<VariantResponse>) {
        respondText(json.encodeToString(body), ContentType.Application.Json)
    }

    private fun gadgetStatus(status: GadgetStatus?) = GadgetResponse(
        ok = status?.ready == true,
        message = when {
            status == null -> "Гаджет не управляется приложением"
            status.ready -> "Устройство прикидывается COM-портом и клавиатурой"
            else -> "Гаджет не настроен"
        },
        supported = status?.supported ?: false,
        udc = status?.udc,
        udcState = status?.udcState,
        functions = status?.functions ?: emptyList(),
        serialDevice = status?.serialDevice,
        keyboardDevice = status?.keyboardDevice,
        problems = status?.problems ?: emptyList(),
    )

    @Serializable
    private data class GadgetResponse(
        val ok: Boolean,
        val message: String,
        val supported: Boolean = false,
        val udc: String? = null,
        val udcState: String? = null,
        val functions: List<String> = emptyList(),
        val serialDevice: String? = null,
        val keyboardDevice: String? = null,
        val problems: List<String> = emptyList(),
    )

    @Serializable
    private data class FileResponse(val name: String, val size: Long)

    @Serializable
    private data class LaunchResponse(val name: String, val commands: String)

    @Serializable
    private data class VariantResponse(
        val name: String,
        val launches: List<LaunchResponse>,
        val files: List<FileResponse>,
    )

    @Serializable
    private data class TypeFileRequest(val variant: String, val file: String)

    @Serializable
    private data class LaunchRequest(val variant: String, val launch: String)

    @Serializable
    private data class ActionResponse(
        val ok: Boolean,
        val message: String,
        val bytes: Long = 0,
    )

    @Serializable
    private data class StatusResponse(
        val mode: String,
        val variants: Int,
        val selfFile: String?,
    )
}
