package pw.binom.webdav

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.junit.After
import org.junit.Before
import org.junit.Test
import pw.binom.webdav.fs.local.LocalFileSystem
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private fun testRun(block: suspend () -> Unit) {
    runBlocking { withTimeout(30.seconds) { block() } }
}

class LocalFileSystemTest {

    private val tempDir = Path(System.getProperty("java.io.tmpdir"), "webdav-test-${System.nanoTime()}")
    private val fs = LocalFileSystem(tempDir)

    private suspend fun readAll(path: Path, range: LongRange? = null): ByteArray {
        val chunks = mutableListOf<ByteArray>()
        fs.readFile(path, range) { chunks.add(it) }.getOrThrow()
        return if (chunks.size == 1) chunks[0] else chunks.fold(ByteArray(0)) { acc, c -> acc + c }
    }

    private suspend fun writeAll(path: Path, content: ByteArray, overwrite: Boolean = true) {
        var called = false
        fs.writeFile(path, overwrite) {
            if (!called) { called = true; content } else null
        }.getOrThrow()
    }

    @Before
    fun setUp() {
        SystemFileSystem.createDirectories(tempDir)
    }

    @After
    fun tearDown() {
        deleteRecursively(tempDir)
    }

    @Test
    fun `create and list directory`() = testRun {
        val dir = Path("testdir")
        fs.createDirectory(dir).getOrThrow()
        val listing = fs.list(Path(".")).getOrThrow()
        assertContains(listing.map { it.path.name }, "testdir")
    }

    @Test
    fun `write and read file`() = testRun {
        val content = "Hello, WebDAV!".encodeToByteArray()
        writeAll(Path("hello.txt"), content)
        val readBack = readAll(Path("hello.txt"))
        assertTrue(content.contentEquals(readBack))
    }

    @Test
    fun `read file with range`() = testRun {
        writeAll(Path("range.txt"), "0123456789".encodeToByteArray())
        val partial = readAll(Path("range.txt"), 2L..5L)
        assertEquals("2345", partial.decodeToString())
    }

    @Test
    fun `delete file`() = testRun {
        val path = Path("todelete.txt")
        val writeResult = fs.writeFile(path, overwrite = true) { null }
        assertTrue(writeResult.isSuccess)
        val metaAfterWrite = fs.getMetadata(path)
        assertTrue(metaAfterWrite.isSuccess)
        fs.delete(path).getOrThrow()
        val metaAfterDelete = fs.getMetadata(path)
        assertTrue(metaAfterDelete.isFailure)
    }

    @Test
    fun `move file`() = testRun {
        val writeResult = fs.writeFile(Path("source.txt"), overwrite = true) { "move me".encodeToByteArray() }
        assertTrue(writeResult.isSuccess)
        fs.move(Path("source.txt"), Path("dest.txt")).getOrThrow()
        assertTrue(fs.getMetadata(Path("source.txt")).isFailure)
        assertTrue(fs.getMetadata(Path("dest.txt")).isSuccess)
    }

    @Test
    fun `copy file`() = testRun {
        fs.writeFile(Path("src.txt"), overwrite = true) { "copy me".encodeToByteArray() }.getOrThrow()
        fs.copy(Path("src.txt"), Path("dst.txt")).getOrThrow()
        assertTrue(fs.getMetadata(Path("src.txt")).isSuccess)
        assertTrue(fs.getMetadata(Path("dst.txt")).isSuccess)
    }

    @Test
    fun `get metadata`() = testRun {
        fs.writeFile(Path("meta.txt"), overwrite = true) { "data".encodeToByteArray() }.getOrThrow()
        val meta = fs.getMetadata(Path("meta.txt")).getOrThrow()
        assertTrue(meta.isRegularFile)
        assertFalse(meta.isDirectory)
        assertEquals(4, meta.size)
    }

    @Test
    fun `not found returns failure`() = testRun {
        val result = fs.getMetadata(Path("/nonexistent"))
        assertTrue(result.isFailure)
    }

    private fun deleteRecursively(path: Path) {
        val meta = SystemFileSystem.metadataOrNull(path) ?: return
        if (meta.isDirectory) {
            SystemFileSystem.list(path).forEach { deleteRecursively(it) }
        }
        SystemFileSystem.delete(path, mustExist = false)
    }
}
