package pw.binom.proxy.front

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pw.binom.hid.TypingProfile
import pw.binom.properties.Configuration

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
) : AutoCloseable {
    companion object {
        fun create(front: Configuration.Front, selfPath: String?): FrontServer {
            val service = BootstrapService(
                bootstrapDir = front.bootstrapDir,
                settingsFile = front.settingsFile,
                exclude = front.bootstrapExclude,
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
            )
            return FrontServer(front.bind, front.port, service)
        }

        private val json = Json { encodeDefaults = true }
    }

    private val logger = KotlinLogging.logger("FrontServer")

    private val server = embeddedServer(CIO, port = port, host = bind) {
        frontModule()
    }

    init {
        logger.info { "Starting front server on $bind:$port (bootstrap dir=${bootstrap.files().size} files)" }
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
                        bootstrapFiles = bootstrap.files().size,
                        selfFile = bootstrap.selfFile?.name,
                    ),
                )
            }

            get("/api/bootstrap/files") {
                call.respondJson(bootstrap.files().map { FileResponse(it.id, it.name, it.size) })
            }

            post("/api/bootstrap/type/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respondJson(ActionResponse(ok = false, message = "Неверный номер файла"))
                    return@post
                }
                val result = bootstrap.typeFile(id)
                call.respondJson(ActionResponse(result.ok, result.message))
            }

            post("/api/bootstrap/settings") {
                val result = bootstrap.typeSettings()
                call.respondJson(ActionResponse(result.ok, result.message))
            }

            post("/api/bootstrap/send-self") {
                val result = bootstrap.sendSelf()
                call.respondJson(
                    ActionResponse(
                        ok = result.ok,
                        message = result.message,
                        bytes = result.bytes,
                        ack = result.ack,
                    ),
                )
            }
        }
    }

    private suspend inline fun <reified T> ApplicationCall.respondJson(body: T) {
        respondText(json.encodeToString(body), ContentType.Application.Json)
    }

    private suspend fun ApplicationCall.respondJson(body: List<FileResponse>) {
        respondText(json.encodeToString(body), ContentType.Application.Json)
    }

    @Serializable
    private data class FileResponse(val id: Int, val name: String, val size: Long)

    @Serializable
    private data class ActionResponse(
        val ok: Boolean,
        val message: String,
        val bytes: Long = 0,
        val ack: Boolean = false,
    )

    @Serializable
    private data class StatusResponse(
        val mode: String,
        val bootstrapFiles: Int,
        val selfFile: String?,
    )
}
