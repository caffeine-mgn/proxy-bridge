package pw.binom.proxy

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.file
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import pw.binom.channel.FileChannel

class FileServer : KoinComponent, AutoCloseable {
    private val logger = KotlinLogging.logger { }
    private val fileChannel by lazy { getKoin().get<FileChannel>() }

    init {
        logger.info { "Starting server on 0.0.0.0:8075" }
    }

    @Serializable
    data class FileResponse(val name: String, val isFile: Boolean, val size: ULong)

    val httpServer = embeddedServer(CIO, port = 8075) {
        install(ContentNegotiation) {
            json()
        }
        install(RoutingRoot) {
            routing {
                get("/files/list/{address...}") {
                    val path = call.parameters.getAll("address")!!.joinToString("/")
                    println("path=$path")
                    val files = fileChannel.getFiles("/$path")
                        .map {
                            FileResponse(
                                name = it.name,
                                isFile = it.isFile,
                                size = it.size,
                            )
                        }
                    call.respond(status = HttpStatusCode.OK, message = files)
                }
                get("/files/blob/{address...}") {
                    val path = call.parameters.getAll("address")!!.joinToString("/")
                    println("path=$path")
                    try {
                        call.respondBytesWriter {
                            fileChannel.getFile("/$path", this)
                        }
                    } catch (e: FileChannel.FileException) {
                        logger.warn(e) { "File error: /$path" }
                    }
                }
            }
        }
    }.start(wait = false)


    override fun close() {
        httpServer.stop()
    }
}
