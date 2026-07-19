package pw.binom.webdav.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.remaining
import kotlinx.io.Buffer
import kotlinx.io.files.Path
import pw.binom.webdav.fs.WebDavFileSystem

fun Application.webDavModule(
    fileSystem: WebDavFileSystem,
    basePath: String = "/dav",
) {
    println("[WebDAV] Configuring module: basePath=$basePath")

    routing {
        route(basePath) {
            println("[WebDAV] Registering handlers at: $basePath")
            installWebDavHandlers(fileSystem, basePath)
            route("/") {
                installWebDavHandlers(fileSystem, basePath)
            }
            route("{...}") {
                installWebDavHandlers(fileSystem, basePath)
            }
        }

        // catch-all for unmatched (registered LAST)
        route("/") {
            handle {
                println("[WebDAV] UNMATCHED ROOT: ${call.request.path()}")
                call.respond(HttpStatusCode.NotFound)
            }
        }
        route("{...}") {
            handle {
                println("[WebDAV] UNMATCHED: ${call.request.path()}")
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}

private fun Route.installWebDavHandlers(fileSystem: WebDavFileSystem, basePath: String) {
    options {
        call.response.headers.append("DAV", "1,2")
        call.response.headers.append(
            "Allow",
            "GET,PUT,DELETE,MKCOL,PROPFIND,LOCK,UNLOCK,MOVE,COPY,HEAD,OPTIONS"
        )
        call.respond(HttpStatusCode.OK)
    }

    method(HttpMethod("PROPFIND")) {
        handle {
            val depth = call.request.header("Depth")?.toIntOrNull() ?: 0
            val targetPath = resolvePath(call, basePath)

            val metadata = fileSystem.getMetadata(targetPath).getOrElse {
                return@handle call.respond(HttpStatusCode.NotFound)
            }

            val xml = buildPropfindXml(fileSystem, targetPath, metadata, depth, basePath)
            call.response.header("Content-Type", "application/xml; charset=utf-8")
            call.respond(HttpStatusCode.MultiStatus, xml)
        }
    }

    method(HttpMethod("LOCK")) {
        handle {
            handleLock(call)
        }
    }

    method(HttpMethod("UNLOCK")) {
        handle {
            call.respond(HttpStatusCode.NoContent)
        }
    }

    method(HttpMethod("MKCOL")) {
        handle {
            val targetPath = resolvePath(call, basePath)
            val exists = fileSystem.getMetadata(targetPath).isSuccess
            if (exists) {
                call.respond(HttpStatusCode.MethodNotAllowed)
            } else {
                fileSystem.createDirectory(targetPath).getOrElse {
                    return@handle call.respond(
                        HttpStatusCode.InternalServerError,
                        it.message ?: "Failed to create directory"
                    )
                }
                call.respond(HttpStatusCode.Created)
            }
        }
    }

    get {
        val targetPath = resolvePath(call, basePath)
        val metadata = fileSystem.getMetadata(targetPath).getOrElse {
            return@get call.respond(HttpStatusCode.NotFound)
        }
        if (metadata.isDirectory) {
            return@get call.respond(HttpStatusCode.NotFound)
        }

        val etag = generateETag(metadata)
        call.response.header("ETag", etag)
        if (metadata.lastModified > 0) {
            call.response.header("Last-Modified", formatHttpDate(metadata.lastModified))
        }

        val ifNoneMatch = call.request.header("If-None-Match")
        if (ifNoneMatch != null && matchETag(ifNoneMatch, etag)) {
            return@get call.respond(HttpStatusCode.NotModified)
        }

        val fileSize = metadata.size
        val rangeHeader = call.request.header("Range")
        val byteRange = parseRange(rangeHeader, fileSize)
        val range = byteRange?.let { it.first..it.last }

        if (byteRange != null) {
            call.response.status(HttpStatusCode.PartialContent)
            call.response.header("Content-Range", "bytes ${byteRange.first}-${byteRange.last}/$fileSize")
        }
        call.response.header("Accept-Ranges", "bytes")

        call.respondBytesWriter(contentType = ContentType.Application.OctetStream) {
            println("GET: $targetPath ---------------")
            val source = fileSystem.readFile(targetPath, range)
            source.use { src ->
                val buf = Buffer()
                try {
                    while (true) {
                        val read = src.readAtMostTo(buf, 8192)
                        if (read <= 0) {
                            println("EOF")
                            break
                        }
                        val size = buf.size.toInt()
                        val bytes = ByteArray(size)
                        buf.readAtMostTo(sink = bytes, startIndex = 0, endIndex = size)
                        writeFully(bytes)
                        flush()
                        println("Sending ${bytes.size} bytes")

                    }
                } catch (e: Throwable) {
                    println("FINISHED!!!")
                    e.printStackTrace()
                }
            }

        }
    }

    put {
        val targetPath = resolvePath(call, basePath)
        val existingMeta = fileSystem.getMetadata(targetPath).getOrNull()
        val etag = if (existingMeta != null) generateETag(existingMeta) else null

        val ifMatch = call.request.header("If-Match")
        if (ifMatch != null && etag != null && !matchETag(ifMatch, etag)) {
            return@put call.respond(HttpStatusCode.PreconditionFailed)
        }
        if (ifMatch?.trim() == "*" && existingMeta == null) {
            return@put call.respond(HttpStatusCode.PreconditionFailed)
        }

        try {
            val sink = fileSystem.writeFile(targetPath, overwrite = true)
            sink.use { s ->
                val channel = call.request.receiveChannel()
                while (!channel.isClosedForRead) {
                    val packet = channel.readRemaining() ?: break
                    if (packet.exhausted()) break
                    val sinkBuf = Buffer()
                    packet.transferTo(sinkBuf)
                    while (sinkBuf.size > 0) {
                        val chunkSize = minOf(sinkBuf.size, 65536L).toInt()
                        val chunk = ByteArray(chunkSize)
                        val read = sinkBuf.readAtMostTo(chunk, 0, chunkSize)
                        val tmp = Buffer()
                        tmp.write(chunk, 0, read)
                        s.write(tmp, tmp.size)
                    }
                }
                s.flush()
            }
            call.respond(HttpStatusCode.Created)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, e.message ?: "Write failed")
        }
    }

    delete {
        val targetPath = resolvePath(call, basePath)
        val metadata = fileSystem.getMetadata(targetPath).getOrNull()
        if (metadata == null) {
            return@delete call.respond(HttpStatusCode.NotFound)
        }

        val etag = generateETag(metadata)
        val ifMatch = call.request.header("If-Match")
        if (ifMatch != null && !matchETag(ifMatch, etag)) {
            return@delete call.respond(HttpStatusCode.PreconditionFailed, "ETag mismatch")
        }

        fileSystem.delete(targetPath).getOrElse {
            return@delete call.respond(HttpStatusCode.InternalServerError, it.message ?: "Failed to delete")
        }
        call.respond(HttpStatusCode.NoContent)
    }

    method(HttpMethod("MOVE")) {
        handle {
            handleMoveCopy(call, fileSystem, basePath, isMove = true)
        }
    }

    method(HttpMethod("COPY")) {
        handle {
            handleMoveCopy(call, fileSystem, basePath, isMove = false)
        }
    }
}

private fun resolvePath(call: RoutingCall, basePath: String): Path {
    val fullPath = call.request.path()
    val raw = fullPath.removePrefix(basePath).takeIf { it.isNotEmpty() } ?: "/"
    val relative = raw.removePrefix("/")
    val decoded = java.net.URLDecoder.decode(relative, "UTF-8")
    return if (decoded.isEmpty()) Path(".") else Path(decoded)
}

private fun generateETag(metadata: pw.binom.webdav.fs.FileMetadata): String {
    return "\"${metadata.lastModified}-${metadata.size}\""
}

private fun matchETag(headerValue: String, target: String): Boolean {
    if (headerValue.trim() == "*") return true
    val targetInner = target.removeSurrounding("\"")
    return headerValue.split(",").map { it.trim().removeSurrounding("\"") }.contains(targetInner)
}

private data class ByteRange(val first: Long, val last: Long)

private fun parseRange(header: String?, fileSize: Long): ByteRange? {
    if (header == null || !header.startsWith("bytes=")) return null
    val spec = header.substring(6).trim()
    val parts = spec.split("-")
    if (parts.size != 2) return null
    val start = parts[0].toLongOrNull() ?: 0L
    val end = parts[1].toLongOrNull() ?: fileSize - 1
    return if (start in 0 until fileSize && end >= start) {
        ByteRange(start, minOf(end, fileSize - 1))
    } else null
}

private suspend fun buildPropfindXml(
    fileSystem: WebDavFileSystem,
    path: Path,
    metadata: pw.binom.webdav.fs.FileMetadata,
    depth: Int,
    basePath: String,
): String {
    return buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        appendLine("<D:multistatus xmlns:D=\"DAV:\">")
        appendPropfindEntry(this, fileSystem, path, metadata, depth, basePath)
        appendLine("</D:multistatus>")
    }
}

private suspend fun appendPropfindEntry(
    sb: StringBuilder,
    fileSystem: WebDavFileSystem,
    path: Path,
    metadata: pw.binom.webdav.fs.FileMetadata,
    depth: Int,
    basePath: String,
) {
    val href = buildHref(path, basePath)
    val etag = generateETag(metadata)
    val lastModified = if (metadata.lastModified > 0) formatHttpDate(metadata.lastModified) else ""

    sb.appendLine("<D:response>")
    sb.appendLine("<D:href>$href</D:href>")
    sb.appendLine("<D:propstat>")
    sb.appendLine("<D:prop>")
    sb.appendLine("<D:getcontentlength>${if (metadata.isDirectory) 0 else metadata.size}</D:getcontentlength>")
    if (lastModified.isNotEmpty()) {
        sb.appendLine("<D:getlastmodified>$lastModified</D:getlastmodified>")
    }
    sb.appendLine("<D:resourcetype>${if (metadata.isDirectory) "<D:collection/>" else ""}</D:resourcetype>")
    sb.appendLine("<D:getetag>$etag</D:getetag>")
    sb.appendLine("</D:prop>")
    sb.appendLine("<D:status>HTTP/1.1 200 OK</D:status>")
    sb.appendLine("</D:propstat>")
    sb.appendLine("</D:response>")

    if (metadata.isDirectory && depth > 0) {
        val children = fileSystem.list(path).getOrNull() ?: return
        val newDepth = if (depth == 1) 0 else depth
        for (child in children) {
            appendPropfindEntry(sb, fileSystem, child.path, child, newDepth, basePath)
        }
    }
}

private fun buildHref(path: Path, basePath: String): String {
    val pathStr = path.toString().replace('\\', '/')
    val normalized = if (pathStr.startsWith("/")) pathStr else "/$pathStr"
    val encoded = normalized.split("/").joinToString("/") { segment ->
        if (segment.isEmpty() || segment == ".") segment
        else java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
    }
    return "$basePath$encoded"
}

private fun formatHttpDate(epochMillis: Long): String {
    val instant = java.time.Instant.ofEpochMilli(epochMillis)
    val formatter = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.withZone(java.time.ZoneId.of("GMT"))
    return formatter.format(instant)
}

private suspend fun handleLock(call: RoutingCall) {
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

private suspend fun handleMoveCopy(
    call: RoutingCall,
    fileSystem: WebDavFileSystem,
    basePath: String,
    isMove: Boolean,
) {
    val sourcePath = resolvePath(call, basePath)
    val destHeader =
        call.request.headers["Destination"] ?: return call.respond(HttpStatusCode.BadRequest, "Missing Destination")

    val destUri = java.net.URI.create(destHeader)
    val destRaw = destUri.path.removePrefix(basePath).takeIf { it.isNotEmpty() } ?: "/"
    val destRelative = destRaw.removePrefix("/")
    val destPath = if (destRelative.isEmpty()) Path(".") else Path(destRelative)

    val overwrite = call.request.headers["Overwrite"]?.lowercase() != "f"
    val destExists = fileSystem.getMetadata(destPath).isSuccess
    if (!overwrite && destExists) {
        return call.respond(HttpStatusCode.PreconditionFailed, "Destination exists, Overwrite: F")
    }

    val result = if (isMove) {
        fileSystem.move(sourcePath, destPath).getOrElse {
            return call.respond(HttpStatusCode.InternalServerError, "Failed to move: ${it.message}")
        }
    } else {
        fileSystem.copy(sourcePath, destPath).getOrElse {
            return call.respond(HttpStatusCode.InternalServerError, "Failed to copy: ${it.message}")
        }
    }

    if (!result.success) {
        return call.respond(HttpStatusCode.InternalServerError, result.errorMessage ?: "Unknown error")
    }

    val status = if (!overwrite || !destExists) HttpStatusCode.Created else HttpStatusCode.NoContent
    call.respond(status)
}
