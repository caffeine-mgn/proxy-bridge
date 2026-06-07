package pw.binom.webdav

import kotlinx.coroutines.runBlocking
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

class LocalFileSystemTest {

    private val tempDir = Path(System.getProperty("java.io.tmpdir"), "webdav-test-${System.nanoTime()}")
    private val fs = LocalFileSystem(tempDir)

    @Before
    fun setUp() {
        SystemFileSystem.createDirectories(tempDir)
    }

    @After
    fun tearDown() {
        deleteRecursively(tempDir)
    }

    @Test
    fun `create and list directory`() = runBlocking {
        val dir = Path("testdir")
        fs.createDirectory(dir).getOrThrow()
        val listing = fs.list(Path(".")).getOrThrow()
        assertContains(listing.map { it.path.name }, "testdir")
    }

    @Test
    fun `write and read file`() = runBlocking {
        val content = "Hello, WebDAV!".encodeToByteArray()
        fs.writeFile(Path("hello.txt"), content).getOrThrow()
        val readBack = fs.readFile(Path("hello.txt")).getOrThrow()
        assertTrue(content.contentEquals(readBack))
    }

    @Test
    fun `read file with range`() = runBlocking {
        fs.writeFile(Path("range.txt"), "0123456789".encodeToByteArray()).getOrThrow()
        val partial = fs.readFile(Path("range.txt"), 2L..5L).getOrThrow()
        assertEquals("2345", partial.decodeToString())
    }

    @Test
    fun `delete file`() = runBlocking {
        val path = Path("todelete.txt")
        val writeResult = fs.writeFile(path, byteArrayOf())
        assertTrue(writeResult.isSuccess)
        val metaAfterWrite = fs.getMetadata(path)
        assertTrue(metaAfterWrite.isSuccess)
        fs.delete(path).getOrThrow()
        val metaAfterDelete = fs.getMetadata(path)
        assertTrue(metaAfterDelete.isFailure)
    }

    @Test
    fun `move file`() = runBlocking {
        val writeResult = fs.writeFile(Path("source.txt"), "move me".encodeToByteArray())
        assertTrue(writeResult.isSuccess)
        fs.move(Path("source.txt"), Path("dest.txt")).getOrThrow()
        assertTrue(fs.getMetadata(Path("source.txt")).isFailure)
        assertTrue(fs.getMetadata(Path("dest.txt")).isSuccess)
    }

    @Test
    fun `copy file`() = runBlocking {
        fs.writeFile(Path("src.txt"), "copy me".encodeToByteArray()).getOrThrow()
        fs.copy(Path("src.txt"), Path("dst.txt")).getOrThrow()
        assertTrue(fs.getMetadata(Path("src.txt")).isSuccess)
        assertTrue(fs.getMetadata(Path("dst.txt")).isSuccess)
    }

    @Test
    fun `get metadata`() = runBlocking {
        fs.writeFile(Path("meta.txt"), "data".encodeToByteArray()).getOrThrow()
        val meta = fs.getMetadata(Path("meta.txt")).getOrThrow()
        assertTrue(meta.isRegularFile)
        assertFalse(meta.isDirectory)
        assertEquals(4, meta.size)
    }

    @Test
    fun `not found returns failure`() = runBlocking {
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