package pw.binom.webdav

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import pw.binom.webdav.fs.local.LocalFileSystem
import pw.binom.webdav.server.webDavModule
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class WebDavModuleTest {

    companion object {
        private const val PORT = 18923
        private val tempDir = Path(System.getProperty("java.io.tmpdir"), "webdav-integration-${System.nanoTime()}")
        private var server: EmbeddedServer<*, *>? = null
        private val client = HttpClient(CIO)

        @BeforeClass
        @JvmStatic
        fun setupServer() {
            SystemFileSystem.createDirectories(tempDir)
            try {
                server = runBlocking {
                    withTimeout(30.seconds) {
                        embeddedServer(Netty, port = PORT) {
                            routing {
                                get("/ping") {
                                    call.respondText("pong")
                                }
                                route("/dav") {
                                    get("/ping2") {
                                        call.respondText("pong2")
                                    }
                                }
                            }
                            webDavModule(LocalFileSystem(tempDir))
                        }.apply { start(wait = false) }
                    }
                }
            } catch (e: Exception) {
                throw RuntimeException("Failed to start server", e)
            }
        }

        @AfterClass
        @JvmStatic
        fun teardownServer() {
            server?.stop(1000, 2000)
            client.close()
            removeDir(tempDir)
        }

        private fun removeDir(path: Path) {
            val meta = SystemFileSystem.metadataOrNull(path) ?: return
            if (meta.isDirectory) {
                SystemFileSystem.list(path).forEach { removeDir(it) }
            }
            SystemFileSystem.delete(path, mustExist = false)
        }
    }

    private val baseUrl = "http://127.0.0.1:$PORT"

    private fun testRun(block: suspend () -> Unit) {
        runBlocking { withTimeout(30.seconds) { block() } }
    }

    @Test
    fun `ping works`() = testRun {
        val response = client.get("$baseUrl/ping")
        assertEquals("pong", response.bodyAsText())
    }

    @Test
    fun `OPTIONS returns DAV and Allow headers`() = testRun {
        val response = client.options("$baseUrl/dav/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.headers["DAV"] ?: "", "1")
        assertContains(response.headers["Allow"] ?: "", "PROPFIND")
    }

    @Test
    fun `PUT then GET returns same content`() = testRun {
        val content = "Hello from WebDAV!"
        val putResponse = client.put("$baseUrl/dav/test.txt") {
            setBody(content)
        }
        assertEquals(HttpStatusCode.Created, putResponse.status)

        val getResponse = client.get("$baseUrl/dav/test.txt")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        assertEquals(content, getResponse.bodyAsText())
    }

    @Test
    fun `GET non-existent returns 404`() = testRun {
        val response = client.get("$baseUrl/dav/nonexistent.txt")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `MKCOL creates directory`() = testRun {
        val response = client.request("$baseUrl/dav/newdir") { method = HttpMethod("MKCOL") }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(SystemFileSystem.exists(Path(tempDir, "newdir")))
    }

    @Test
    fun `DELETE removes file`() = testRun {
        client.put("$baseUrl/dav/todelete.txt") { setBody("delete me") }
        val deleteResponse = client.delete("$baseUrl/dav/todelete.txt")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
        assertFalse(SystemFileSystem.exists(Path(tempDir, "todelete.txt")))
    }

    @Test
    fun `PROPFIND returns XML with properties`() = testRun {
        client.put("$baseUrl/dav/propfind-test.txt") { setBody("data") }
        val response = client.request("$baseUrl/dav/propfind-test.txt") { method = HttpMethod("PROPFIND") }
        assertEquals(HttpStatusCode.MultiStatus, response.status)
        val body = response.bodyAsText()
        assertContains(body, "D:multistatus")
        assertContains(body, "getcontentlength")
        assertContains(body, "getetag")
    }

    @Test
    fun `MOVE renames file`() = testRun {
        client.put("$baseUrl/dav/move-src.txt") { setBody("move me") }
        val moveResponse = client.request("$baseUrl/dav/move-src.txt") {
            method = HttpMethod("MOVE")
            header("Destination", "$baseUrl/dav/move-dst.txt")
        }
        assertEquals(HttpStatusCode.Created, moveResponse.status)
        assertFalse(SystemFileSystem.exists(Path(tempDir, "move-src.txt")))
        assertTrue(SystemFileSystem.exists(Path(tempDir, "move-dst.txt")))
    }

    @Test
    fun `COPY duplicates file`() = testRun {
        client.put("$baseUrl/dav/copy-src.txt") { setBody("copy me") }
        val copyResponse = client.request("$baseUrl/dav/copy-src.txt") {
            method = HttpMethod("COPY")
            header("Destination", "$baseUrl/dav/copy-dst.txt")
        }
        assertEquals(HttpStatusCode.Created, copyResponse.status)
        assertTrue(SystemFileSystem.exists(Path(tempDir, "copy-src.txt")))
        assertTrue(SystemFileSystem.exists(Path(tempDir, "copy-dst.txt")))
    }

    @Test
    fun `LOCK returns token`() = testRun {
        val response = client.request("$baseUrl/dav/") { method = HttpMethod("LOCK") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.headers["Lock-Token"] ?: "", "opaquelocktoken:")
    }

    @Test
    fun `UNLOCK returns NoContent`() = testRun {
        val response = client.request("$baseUrl/dav/") { method = HttpMethod("UNLOCK") }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }
}
