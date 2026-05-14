package pw.binom.proxy

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.engine.embeddedServer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.streams.asByteWriteChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.*
import kotlin.math.min

class WebDavServer(port: Int, rootDir: Path) : AutoCloseable {
    companion object {
        fun module(port: Int, rootDir: Path) = org.koin.dsl.module {
            single { WebDavServer(port,rootDir) }
        }
    }

    private val logger = KotlinLogging.logger {}
    private val server = embeddedServer(io.ktor.server.cio.CIO, port = port) {
        webDavModule(
            rootDir.also { Files.createDirectories(it) }
        )
    }

    init {
        logger.info { "Starting WebDav server on 0.0.0:$port" }
        server.start(wait = false)
    }

    override fun close() {
        server.stop()
    }
}




//private val ROOT_DIR = Paths.get("/tmp/webdav-root").also { Files.createDirectories(it) }
private val HTTP_DATE = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"))

@OptIn(ExperimentalPathApi::class)
fun Application.webDavModule(rootDir: Path) {
    routing {
        route("/dav") {
            // 1. OPTIONS
            options {
                call.response.headers.append("DAV", "1,2")
                call.response.headers.append("Allow", "GET,PUT,DELETE,MKCOL,PROPFIND,LOCK,UNLOCK,MOVE,COPY,HEAD,OPTIONS")
                call.respond(HttpStatusCode.OK)
            }

            // 2. PROPFIND
            method(HttpMethod("PROPFIND")) {
                handle {
                    val depth = call.request.header("Depth")?.toIntOrNull() ?: 0
                    val target = resolvePath(call,rootDir)
                    if (!Files.exists(target)) return@handle call.respond(HttpStatusCode.NotFound)

                    val xml = buildString {
                        appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                        appendLine("<D:multistatus xmlns:D=\"DAV:\">")
                        appendPathXml(target, depth,rootDir)
                        appendLine("</D:multistatus>")
                    }
                    call.response.header("Content-Type", "application/xml; charset=utf-8")
                    call.respond(HttpStatusCode.MultiStatus, xml)
                }
            }

            // 3. LOCK / UNLOCK (заглушка)
            method(HttpMethod("LOCK")) { handle { handleLock(call) } }
            method(HttpMethod("UNLOCK")) { handle { call.respond(HttpStatusCode.NoContent) } }

            // 4. MKCOL
            method(HttpMethod("MKCOL")) {
                handle {
                    val path = resolvePath(call,rootDir)
                    if (Files.exists(path)) call.respond(HttpStatusCode.MethodNotAllowed)
                    else {
                        Files.createDirectories(path)
                        call.respond(HttpStatusCode.Created)
                    }
                }
            }

            // 5. GET с поддержкой Range и ETag
            get {
                val path = resolvePath(call,rootDir)
                if (!Files.isRegularFile(path)) return@get call.respond(HttpStatusCode.NotFound)

                val attr = Files.readAttributes(path, BasicFileAttributes::class.java)
                val etag = generateETag(path)
                call.response.header("ETag", etag)
                call.response.header("Last-Modified", HTTP_DATE.format(Instant.ofEpochMilli(attr.lastModifiedTime().toMillis())))

                // Проверка If-None-Match для кэширования
                val ifNoneMatch = call.request.header("If-None-Match")
                if (ifNoneMatch != null && matchETag(ifNoneMatch, etag)) {
                    return@get call.respond(HttpStatusCode.NotModified)
                }

                val fileSize = attr.size()
                val rangeHeader = call.request.header("Range")
                val range = parseRange(rangeHeader, fileSize)

                if (range != null) {
                    // Частичный ответ
                    call.response.status(HttpStatusCode.PartialContent)
                    call.response.header("Content-Range", "bytes ${range.start}-${range.end}/$fileSize")
                    call.response.header("Content-Length", "${range.end - range.start + 1}")
                    call.respondOutputStream {
                        withContext(Dispatchers.IO) {
                            Files.newByteChannel(path, StandardOpenOption.READ).use { channel ->
                                channel.position(range.start)
                                val buf = java.nio.ByteBuffer.allocate(64 * 1024)
                                var remaining = range.end - range.start + 1
                                while (remaining > 0 && channel.read(buf) != -1) {
                                    buf.flip()
                                    val toWrite = min(buf.remaining(), remaining.toInt())
//                                    writeFully(buf.array(), buf.arrayOffset(), toWrite)
                                    write(buf.array(), buf.arrayOffset(), toWrite)
                                    buf.clear()
                                    remaining -= toWrite
                                }
                            }
                        }
                    }
                } else {
                    // Полный ответ
                    call.response.header("Accept-Ranges", "bytes")
                    call.response.header("Content-Length", "$fileSize")
                    call.respondOutputStream {
                        withContext(Dispatchers.IO) {
                            path.inputStream().buffered().use { it.copyTo(this@respondOutputStream, 64 * 1024) }
                        }
                    }
                }
            }

            // 6. PUT с If-Match
            put {
                val path = resolvePath(call,rootDir)
                val etag = generateETag(path)
                val ifMatch = call.request.header("If-Match")

                if (ifMatch != null && !matchETag(ifMatch, etag) && ifMatch.trim() != "*") {
                    return@put call.respond(HttpStatusCode.PreconditionFailed, "ETag mismatch")
                }
                if (ifMatch?.trim() == "*" && !Files.exists(path)) {
                    return@put call.respond(HttpStatusCode.PreconditionFailed, "File must exist for If-Match: *")
                }

                Files.createDirectories(path.parent)
                withContext(Dispatchers.IO) {
                    call.request.receiveChannel().copyTo(path.outputStream().asByteWriteChannel(), 64 * 1024)
                }
                call.respond(HttpStatusCode.Created)
            }

            // 7. DELETE с If-Match
            delete {
                val path = resolvePath(call,rootDir)
                if (!Files.exists(path)) return@delete call.respond(HttpStatusCode.NotFound)

                val etag = generateETag(path)
                val ifMatch = call.request.header("If-Match")
                if (ifMatch != null && !matchETag(ifMatch, etag)) {
                    return@delete call.respond(HttpStatusCode.PreconditionFailed, "ETag mismatch")
                }

                withContext(Dispatchers.IO) { path.deleteRecursively() }
                call.respond(HttpStatusCode.NoContent)
            }

            // 8. MOVE / COPY
            method(HttpMethod("MOVE")) { handle { handleMoveCopy(call, isMove = true,rootDir) } }
            method(HttpMethod("COPY")) { handle { handleMoveCopy(call, isMove = false,rootDir) } }
        }
    }
}

// ================= HELPERS =================

private fun resolvePath(call: RoutingCall,ROOT_DIR: Path): Path {
    val raw = call.request.path().removePrefix("/dav").takeIf { it.isNotEmpty() } ?: ""
    val normalized = Paths.get(ROOT_DIR.toString(), raw).normalize()
    if (!normalized.startsWith(ROOT_DIR)) throw SecurityException("Path traversal")
    return normalized
}

private fun generateETag(path: Path): String {
    val attr = Files.readAttributes(path, BasicFileAttributes::class.java)
    return "\"${attr.lastModifiedTime().toMillis()}-${attr.size()}\""
}

private fun matchETag(headerValue: String, target: String): Boolean {
    if (headerValue.trim() == "*") return true
    return headerValue.split(",").map { it.trim().removeSurrounding("\"") }.contains(target.removeSurrounding("\""))
}

private data class ByteRange(val start: Long, val end: Long)
private fun parseRange(header: String?, fileSize: Long): ByteRange? {
    if (header == null || !header.startsWith("bytes=")) return null
    val spec = header.substring(6).trim()
    val parts = spec.split("-")
    if (parts.size != 2) return null
    val start = parts[0].toLongOrNull() ?: 0L
    val end = parts[1].toLongOrNull() ?: fileSize - 1
    return if (start in 0 until fileSize && end >= start) {
        ByteRange(start, min(end, fileSize - 1))
    } else null
}

private fun StringBuilder.appendPathXml(path: Path, depth: Int,ROOT_DIR: Path) {
    val isDir = Files.isDirectory(path)
    val attr = Files.readAttributes(path, BasicFileAttributes::class.java)
    val relative = "/" + ROOT_DIR.relativize(path).toString().replace('\\', '/')

    appendLine("<D:response>")
    appendLine("<D:href>/dav${relative}</D:href>")
    appendLine("<D:propstat>")
    appendLine("<D:prop>")
    appendLine("<D:getcontentlength>${if (isDir) 0 else attr.size()}</D:getcontentlength>")
    appendLine("<D:getlastmodified>${HTTP_DATE.format(Instant.ofEpochMilli(attr.lastModifiedTime().toMillis()))}</D:getlastmodified>")
    appendLine("<D:resourcetype>${if (isDir) "<D:collection/>" else ""}</D:resourcetype>")
    appendLine("<D:getetag>${generateETag(path)}</D:getetag>")
    appendLine("</D:prop>")
    appendLine("<D:status>HTTP/1.1 200 OK</D:status>")
    appendLine("</D:propstat>")
    appendLine("</D:response>")

    if (isDir && depth > 0) {
        Files.list(path).use { stream ->
            stream.forEach { child -> appendPathXml(child, if (depth == 1) 0 else depth,ROOT_DIR) }
        }
    }
}

private suspend fun handleLock(call: io.ktor.server.routing.RoutingCall) {
    val token = "opaquelocktoken:${System.nanoTime()}"
    val xml = """
        <?xml version="1.0" encoding="utf-8"?>
        <D:prop xmlns:D="DAV:"><D:lockdiscovery><D:activelock>
          <D:locktype><D:write/></D:locktype><D:lockscope><D:exclusive/></D:lockscope>
          <D:depth>infinity</D:depth><D:owner/><D:timeout>Second-3600</D:timeout>
          <D:locktoken><D:href>$token</D:href></D:locktoken>
        </D:activelock></D:lockdiscovery></D:prop>
    """.trimIndent()
    call.response.header("Lock-Token", "<$token>")
    call.respond(HttpStatusCode.OK, xml)
}

private suspend fun handleMoveCopy(call: RoutingCall, isMove: Boolean,ROOT_DIR: Path) {
    val source = resolvePath(call,ROOT_DIR)
    val destHeader = call.request.headers["Destination"]
    if (destHeader == null) return call.respond(HttpStatusCode.BadRequest, "Missing Destination")

    val destUri = java.net.URI.create(destHeader)
    val destPath = Paths.get(ROOT_DIR.toString(), destUri.path.removePrefix("/dav")).normalize()
    if (!destPath.startsWith(ROOT_DIR)) return call.respond(HttpStatusCode.BadRequest, "Invalid Destination")

    val overwrite = call.request.headers["Overwrite"]?.lowercase() != "f"
    if (!overwrite && Files.exists(destPath)) {
        return call.respond(HttpStatusCode.PreconditionFailed, "Destination exists, Overwrite: F")
    }

    withContext(Dispatchers.IO) {
        try {
            Files.createDirectories(destPath.parent)
            if (isMove) {
                Files.move(source, destPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } else {
                Files.copy(source, destPath, StandardCopyOption.REPLACE_EXISTING)
            }
            val status = if (Files.notExists(destPath) || !overwrite) HttpStatusCode.Created else HttpStatusCode.NoContent
            call.respond(status)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "Failed: ${e.message}")
        }
    }
}
