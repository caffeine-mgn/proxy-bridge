package pw.binom.webdav

import pw.binom.*
import pw.binom.date.DateTime
import pw.binom.date.rfc822
import pw.binom.io.*
import pw.binom.io.http.HTTPMethod
import pw.binom.io.http.Headers
import pw.binom.io.http.headersOf
import pw.binom.io.http.range.Range
import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.url.Path
import pw.binom.url.UrlEncoder
import pw.binom.url.toPath
import pw.binom.url.toURL
import pw.binom.xml.dom.findElements
import pw.binom.xml.dom.writeXml
import pw.binom.xml.dom.xmlTree

class FileSystemWebDavHandler(
    fs: Lazy<FileSystem2>,
    private val global: Path,
) : HttpHandler {
    companion object {
        private const val DAV_NS = "DAV:"
        private const val MULTISTATUS_TAG = "multistatus"
        private const val DISPLAY_NAME = "displayname"
        private const val GET_LAST_MODIFIED = "getlastmodified"
        private const val GET_CONTENT_LENGTH = "getcontentlength"
        private const val RESOURCE_TYPE = "resourcetype"
        private const val COLLECTION = "collection"
        private const val STATUS = "status"
        private const val DEPTH = "Depth"
        private const val PROP_STAT = "propstat"
        private const val PROP = "prop"
        private const val RESPONSE = "response"
        private const val HREF = "href"
        private const val OVERWRITE = "Overwrite"
        private const val DESTINATION = "Destination"
    }

    private val fs by fs

    override suspend fun handle(exchange: HttpServerExchange) {
        when (exchange.requestMethod) {
            HTTPMethod.HEAD.code -> exchange.startResponse(405)
            HTTPMethod.OPTIONS.code -> options(exchange)
            HTTPMethod.PROPFIND.code -> propfind(exchange)
            HTTPMethod.GET.code -> getFile(exchange)
            HTTPMethod.MKCOL.code -> makeDirectory(exchange)
            HTTPMethod.DELETE.code -> delete(exchange)
            HTTPMethod.LOCK.code -> lock(exchange)
            HTTPMethod.UNLOCK.code -> unlock(exchange)
            HTTPMethod.PUT.code -> putFile(exchange)
            HTTPMethod.MOVE.code -> move(exchange)
            HTTPMethod.COPY.code -> copy(exchange)
        }
    }

    private val Range.toFs
        get() = when (this) {
            is Range.StartFrom -> FileSystem2.Range.First(start = start)
            is Range.Last -> FileSystem2.Range.Last(size = size)
            is Range.Between -> FileSystem2.Range.Between(start = start, end = end)
        }

    private val HttpServerExchange.overwrite
        get() = requestHeaders[OVERWRITE]?.firstOrNull()?.let { it == "T" } ?: true

    private val HttpServerExchange.destination
        get() = requestHeaders[DESTINATION]
            ?.firstOrNull()
            ?.toURL()
            ?.path
            ?.removePrefix(global)
            ?.fixEndSlash

    private suspend fun putFile(exchange: HttpServerExchange) {
        exchange.input.useAsync { input ->
            fs.writeFile(
                path = exchange.filePath,
                override = exchange.overwrite,
            ).useAsync { output ->
                input.copyTo(output)
            }
        }
        exchange.startResponse(201)
    }

    private suspend fun move(exchange: HttpServerExchange) {
        val destination = exchange.destination
        if (destination == null) {
            exchange.startResponse(400)
            exchange.output.bufferedAsciiWriter().useAsync {
                it.append("Missing header \"Destination\"")
            }
            return
        }
        fs.move(
            from = exchange.filePath,
            to = destination,
            override = exchange.overwrite,
        )
        exchange.startResponse(202)
    }

    private suspend fun copy(exchange: HttpServerExchange) {
        val destination = exchange.destination
        if (destination == null) {
            exchange.startResponse(400)
            exchange.output.bufferedAsciiWriter().useAsync {
                it.append("Missing header \"Destination\"")
            }
            return
        }
        fs.copy(
            from = exchange.filePath,
            to = destination,
            override = exchange.overwrite,
        )
        exchange.startResponse(202)
    }

    private suspend fun lock(exchange: HttpServerExchange) {
        val fs = (fs as? FileSystem2Lockable)
        if (fs == null) {
            exchange.startResponse(405)
            return
        }
        fs.lock(exchange.filePath)
        exchange.startResponse(200)
    }

    private suspend fun unlock(exchange: HttpServerExchange) {
        val fs = (fs as? FileSystem2Lockable)
        if (fs == null) {
            exchange.startResponse(405)
            return
        }
        fs.unlock(exchange.filePath)
        exchange.startResponse(200)
    }

    private suspend fun delete(exchange: HttpServerExchange) {
        val ee = exchange.path.removePrefix(global).fixEndSlash
        if (fs.getEntity(ee) == null) {
            exchange.startResponse(404)
            return
        }
        fs.delete(ee, true)
        exchange.startResponse(200)
    }

    val HttpServerExchange.filePath
        get() = path.removePrefix(global).fixEndSlash.let { UrlEncoder.pathDecode(it.raw).toPath }

    private suspend fun makeDirectory(exchange: HttpServerExchange) {
        fs.makeDirectories(exchange.filePath)
        exchange.startResponse(201)
    }

    private suspend fun getFile(exchange: HttpServerExchange) {
        val filePath = exchange.filePath
        val entity = fs.getEntity(filePath)
        if (entity == null) {
            exchange.startResponse(404)
            return
        }
        val range = exchange.inputHeaders.range.firstOrNull()
        if (range != null && range.unit != "bytes") {
            exchange.startResponse(400)
            exchange.output.bufferedWriter().useAsync {
                it.append("Invalid range unit. Supported only \"bytes\"")
            }
            return
        }

        println("Range: $range")
        val fsRange = range?.toFs

        val fileStream = fs.readFile(filePath, fsRange ?: FileSystem2.Range.First(0))
        if (fileStream == null) {
            exchange.startResponse(404)
            return
        }

        fileStream.useAsync { fileStream ->
            exchange.response().also {
                it.status = 200
                it.headers.contentLength = entity.size.toULong()
                if (range != null) {
                    it.headers.range = listOf(range)
                }
                it.startOutput().useAsync {
                    fileStream.copyTo(it)
                }
            }
        }
    }

    private suspend fun getEntries(path: Path, depth: Int): List<FileSystem2.Entity>? =
        fs.getEntitiesWithDepth(path, depth)

    val Path.withoutEndSlash
        get() = if (raw.endsWith("/")) {
            raw.substring(0, raw.length - 1).toPath
        } else {
            this
        }

    val Path.withEndSlash
        get() = if (raw.endsWith("/")) {
            this
        } else {
            "${raw}/".toPath
        }

    val Path.fixEndSlash
        get() = when {
            this == "/".toPath -> this
            this.isEmpty -> "/".toPath
            else -> withoutEndSlash
        }

    private suspend fun propfind(exchange: HttpServerExchange) {
        val depth = exchange.inputHeaders.getSingleOrNull(DEPTH)?.toInt() ?: 0
        val entities = getEntries(exchange.filePath, depth)
        if (entities == null) {
            exchange.startResponse(404)
            return
        }


        val hasRequestBody = exchange.inputHeaders[Headers.CONTENT_TYPE]
            ?.any { it == "application/xml" } ?: false
        val properties = if (hasRequestBody) {
            exchange.input.bufferedAsciiReader().useAsync {
                it.xmlTree()
                    .findElements { it.tag.endsWith("prop") }.first().childs
                    .asSequence()
                    .filter { it.nameSpace == DAV_NS }
                    .map {
                        it.tag
                    }.toMutableSet()
            }
        } else {
            setOf(
                GET_CONTENT_LENGTH,
                GET_LAST_MODIFIED,
                RESOURCE_TYPE,
                DISPLAY_NAME,
            )
        }

        exchange.startResponse(207, headersOf(Headers.CONTENT_TYPE to "application/xml; charset=UTF-8"))
        exchange.output.bufferedWriter().useAsync {
            it.buildListResponse(entities, properties)
        }
    }

    suspend fun FileSystem2.getEntitiesWithDepth(
        path: Path,
        depth: Int,
    ): List<FileSystem2.Entity>? {
        val out = ArrayList<FileSystem2.Entity>()
        val e = getEntity(path) ?: return null
        out += e

        if (!e.isFile) {
            getD(path, depth, out)
        }

        return out
    }

    private suspend fun FileSystem2.getD(
        path: Path,
        d: Int,
        out: ArrayList<FileSystem2.Entity>,
    ) {
        if (d <= 0) {
            return
        }
        getEntries(path)?.forEach {
            out.add(it)
            if (!it.isFile) {
                getD(it.path, d - 1, out)
            }
        }
    }

    private suspend fun AsyncAppendable.buildListResponse(
        entities: List<FileSystem2.Entity>,
        properties: Set<String>,
    ) {
        writeXml("UTF-8") {
            node(MULTISTATUS_TAG, DAV_NS) {
                entities.forEach { e ->
                    node(RESPONSE, DAV_NS) {
                        node(HREF, DAV_NS) {

                            val href = (global.append(e.path)).let {
                                if (e.isFile) {
                                    it.fixEndSlash
                                } else {
                                    it.withEndSlash
                                }
                            }
                            value(href.raw)
                        }
                        node(PROP_STAT, DAV_NS) {
                            node(PROP, DAV_NS) {
                                properties.forEach { prop ->
                                    when {
                                        prop == DISPLAY_NAME ->
                                            node(
                                                DISPLAY_NAME,
                                                DAV_NS,
                                            ) {
                                                value(e.name)
                                            }

                                        prop == GET_LAST_MODIFIED ->
                                            node(GET_LAST_MODIFIED, DAV_NS) {
                                                value(DateTime(e.lastModified).rfc822())
                                            }

                                        prop == GET_CONTENT_LENGTH ->
                                            node(GET_CONTENT_LENGTH, DAV_NS) {
                                                if (e.isFile) {
                                                    value(e.size.toString())
                                                } else {
                                                    value("0")
                                                }
                                            }

                                        prop == RESOURCE_TYPE -> {
                                            if (e.isFile) {
                                                node(RESOURCE_TYPE, DAV_NS)
                                            } else {
                                                node(RESOURCE_TYPE, DAV_NS) {
                                                    node(COLLECTION, DAV_NS)
                                                }
                                            }
                                        }
//                                        else -> node(prop.second, prop.first)
                                    }
                                }
                            }
                            node(STATUS, DAV_NS) {
                                try {
//                                        fs.get(e.path)
                                    value("HTTP/1.1 200 OK")
                                } catch (e: Throwable) {
                                    value("HTTP/1.1 403 Forbidden")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun options(exchange: HttpServerExchange) {
        val availableMethods = HashSet<HTTPMethod>()
        availableMethods += HTTPMethod.OPTIONS
        availableMethods += HTTPMethod.GET
        availableMethods += HTTPMethod.POST
        availableMethods += HTTPMethod.PUT
        availableMethods += HTTPMethod.DELETE
        availableMethods += HTTPMethod.TRACE
        availableMethods += HTTPMethod.COPY
        availableMethods += HTTPMethod.MOVE
        availableMethods += HTTPMethod.MKCOL
        availableMethods += HTTPMethod.PROPFIND
        availableMethods += HTTPMethod.PROPPATCH
        availableMethods += HTTPMethod.ORDERPATCH
        if (fs is FileSystem2Lockable) {
            availableMethods += HTTPMethod.LOCK
            availableMethods += HTTPMethod.UNLOCK
        }

        exchange.startResponse(
            statusCode = 200,
            Headers.ALLOW to availableMethods.joinToString(separator = ", ") { it.code },
            Headers.DAV to "1, 2, ordered-collections",
        )
    }
}
