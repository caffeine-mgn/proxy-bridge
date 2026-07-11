package pw.binom.proxy

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.engine.embeddedServer
import io.ktor.server.cio.CIO
import org.koin.dsl.module
import pw.binom.webdav.fs.WebDavFileSystem
import pw.binom.webdav.server.webDavModule

class WebDavServer(
    port: Int,
    bind: String,
    basePath: String,
    fileSystem: WebDavFileSystem,
) : AutoCloseable {
    companion object {
        fun module(port: Int, bind: String, basePath: String, fsName: String) = module {
            single {
                val fs = get<WebDavFileSystem>(org.koin.core.qualifier.named(fsName))
                WebDavServer(port, bind, basePath, fs)
            }
        }
    }

    private val logger = KotlinLogging.logger {}

    private val server = embeddedServer(CIO, port = port, host = bind) {
        webDavModule(fileSystem, basePath)
    }

    init {
        logger.info { "Starting WebDav server on $bind:$port (basePath=$basePath, fs=$fileSystem)" }
        server.start(wait = false)
    }

    override fun close() {
        server.stop(1000, 2000)
    }
}
