package pw.binom.webdav.fs.mounted

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path
import org.junit.Test
import pw.binom.webdav.fs.CopyOrMoveResult
import pw.binom.webdav.fs.FileMetadata
import pw.binom.webdav.fs.WebDavFileSystem
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private fun testRun(block: suspend () -> Unit) {
    runBlocking { withTimeout(30.seconds) { block() } }
}

private class MapFileSystem : WebDavFileSystem {
    private val files = mutableMapOf<String, ByteArray>()

    fun add(path: String, content: ByteArray = byteArrayOf()) {
        files[normalizeKey(path)] = content
    }

    private fun normalizeKey(path: String): String {
        val p = path.removePrefix("/")
        return if (p == ".") "" else p
    }

    override suspend fun list(path: Path): Result<List<FileMetadata>> = runCatching {
        val prefix = normalizeKey(path.toString())
        val dirPrefix = if (prefix.isEmpty()) "" else "$prefix/"
        files.keys.filter { it.startsWith(dirPrefix) && it.removePrefix(dirPrefix).contains("/").not() }
            .map { name ->
                val isDir = files.keys.any { it.startsWith("$name/") }
                FileMetadata(Path("/$name"), isDir, !isDir, files[name]?.size?.toLong() ?: 0, 0)
            }
    }

    override suspend fun getMetadata(path: Path): Result<FileMetadata> = runCatching {
        val key = normalizeKey(path.toString())
        val content = files[key] ?: error("Not found: ${path}")
        val isDir = files.keys.any { it.startsWith("$key/") }
        FileMetadata(path, isDir, !isDir, content.size.toLong(), 0)
    }

    override suspend fun readFile(path: Path, range: LongRange?, onChunk: suspend (ByteArray) -> Unit): Result<Unit> = runCatching {
        val key = normalizeKey(path.toString())
        val content = files[key] ?: error("Not found: ${path}")
        val data = if (range != null) {
            val start = range.first.coerceAtLeast(0)
            val end = range.last.coerceAtMost(content.size.toLong() - 1).coerceAtLeast(start)
            content.copyOfRange(start.toInt(), (end + 1).toInt())
        } else content
        onChunk(data)
    }

    override suspend fun writeFile(path: Path, overwrite: Boolean, nextChunk: suspend () -> ByteArray?): Result<Unit> = runCatching {
        val key = normalizeKey(path.toString())
        if (!overwrite && files.containsKey(key)) error("File exists: $path")
        val chunks = mutableListOf<ByteArray>()
        while (true) {
            val chunk = nextChunk() ?: break
            chunks.add(chunk)
        }
        val size = chunks.sumOf { it.size }
        val data = ByteArray(size)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(data, offset)
            offset += chunk.size
        }
        files[key] = data
    }

    override suspend fun createDirectory(path: Path): Result<Unit> = runCatching {
        files[normalizeKey(path.toString())] = byteArrayOf()
    }

    override suspend fun delete(path: Path): Result<Unit> = runCatching {
        val key = normalizeKey(path.toString())
        files.keys.filter { it == key || it.startsWith("$key/") }.forEach { files.remove(it) }
    }

    override suspend fun move(source: Path, destination: Path): Result<CopyOrMoveResult> = runCatching {
        val srcKey = normalizeKey(source.toString())
        val dstKey = normalizeKey(destination.toString())
        val content = files[srcKey] ?: error("Not found: $source")
        files[dstKey] = content
        files.remove(srcKey)
        CopyOrMoveResult(true)
    }

    override suspend fun copy(source: Path, destination: Path): Result<CopyOrMoveResult> = runCatching {
        val srcKey = normalizeKey(source.toString())
        val dstKey = normalizeKey(destination.toString())
        val content = files[srcKey] ?: error("Not found: $source")
        files[dstKey] = content
        CopyOrMoveResult(true)
    }
}

class MountedFileSystemTest {

    private suspend fun readAll(fs: WebDavFileSystem, path: Path, range: LongRange? = null): ByteArray {
        val chunks = mutableListOf<ByteArray>()
        fs.readFile(path, range) { chunks.add(it) }.getOrThrow()
        return if (chunks.size == 1) chunks[0] else chunks.fold(ByteArray(0)) { acc, c -> acc + c }
    }

    @Test
    fun `single mount routes paths correctly`() = testRun {
        val inner = MapFileSystem()
        inner.add("test/file/1.txt", "data".encodeToByteArray())

        val mounted = MountedFileSystem()
        mounted.mount("/remote", inner)

        val meta = mounted.getMetadata(Path("test/file/1.txt"))
        assertTrue(meta.isFailure, "path without mount prefix should fail")

        val meta2 = mounted.getMetadata(Path("remote/test/file/1.txt"))
        assertTrue(meta2.isSuccess, "path with mount prefix should resolve")
        assertEquals(4, meta2.getOrThrow().size)
    }

    @Test
    fun `two mounts isolate files`() = testRun {
        val fsA = MapFileSystem()
        val fsB = MapFileSystem()
        fsA.add("file.txt", "AAA".encodeToByteArray())
        fsB.add("file.txt", "BBB".encodeToByteArray())

        val mounted = MountedFileSystem()
        mounted.mount("/a", fsA)
        mounted.mount("/b", fsB)

        val fromA = readAll(mounted, Path("a/file.txt"))
        val fromB = readAll(mounted, Path("b/file.txt"))
        assertEquals("AAA", fromA.decodeToString())
        assertEquals("BBB", fromB.decodeToString())
    }

    @Test
    fun `root mount as fallback`() = testRun {
        val specific = MapFileSystem()
        specific.add("data.txt", "specific".encodeToByteArray())
        val fallback = MapFileSystem()
        fallback.add("other.txt", "fallback".encodeToByteArray())

        val mounted = MountedFileSystem()
        mounted.mount("/mnt", specific)
        mounted.mount("/", fallback)

        val fromSpecific = readAll(mounted, Path("mnt/data.txt"))
        val fromFallback = readAll(mounted, Path("other.txt"))
        assertEquals("specific", fromSpecific.decodeToString())
        assertEquals("fallback", fromFallback.decodeToString())
    }

    @Test
    fun `priority by specificity`() = testRun {
        val general = MapFileSystem()
        general.add("file.txt", "general".encodeToByteArray())
        val specific = MapFileSystem()
        specific.add("file.txt", "specific".encodeToByteArray())

        val mounted = MountedFileSystem()
        mounted.mount("/base", general)
        mounted.mount("/base/nested", specific)

        val fromGeneral = readAll(mounted, Path("base/file.txt"))
        val fromSpecific = readAll(mounted, Path("base/nested/file.txt"))
        assertEquals("general", fromGeneral.decodeToString())
        assertEquals("specific", fromSpecific.decodeToString())
    }

    @Test
    fun `unmount removes route`() = testRun {
        val inner = MapFileSystem()
        inner.add("test.txt", "data".encodeToByteArray())

        val mounted = MountedFileSystem()
        mounted.mount("/tmp", inner)
        assertTrue(mounted.getMetadata(Path("tmp/test.txt")).isSuccess)

        mounted.unmount("/tmp")
        assertTrue(mounted.getMetadata(Path("tmp/test.txt")).isFailure)
    }

    @Test
    fun `cross mount copy`() = testRun {
        val fsA = MapFileSystem()
        val fsB = MapFileSystem()
        fsA.add("src.txt", "hello".encodeToByteArray())

        val mounted = MountedFileSystem()
        mounted.mount("/a", fsA)
        mounted.mount("/b", fsB)

        val result = mounted.copy(Path("a/src.txt"), Path("b/dst.txt")).getOrThrow()
        assertTrue(result.success)

        assertEquals("hello", readAll(fsB, Path("dst.txt")).decodeToString())
        assertEquals("hello", readAll(fsA, Path("src.txt")).decodeToString())
    }

    @Test
    fun `cross mount move`() = testRun {
        val fsA = MapFileSystem()
        val fsB = MapFileSystem()
        fsA.add("src.txt", "hello".encodeToByteArray())

        val mounted = MountedFileSystem()
        mounted.mount("/a", fsA)
        mounted.mount("/b", fsB)

        val result = mounted.move(Path("a/src.txt"), Path("b/dst.txt")).getOrThrow()
        assertTrue(result.success)

        assertEquals("hello", readAll(fsB, Path("dst.txt")).decodeToString())
        assertTrue(fsA.getMetadata(Path("src.txt")).isFailure)
    }

    @Test
    fun `same mount move uses direct delegation`() = testRun {
        val inner = MapFileSystem()
        inner.add("src.txt", "data".encodeToByteArray())

        val mounted = MountedFileSystem()
        mounted.mount("/", inner)

        mounted.move(Path("src.txt"), Path("dst.txt")).getOrThrow()
        assertTrue(inner.getMetadata(Path("src.txt")).isFailure)
        assertTrue(inner.getMetadata(Path("dst.txt")).isSuccess)
    }

    @Test
    fun `createDirectory delegates properly`() = testRun {
        val inner = MapFileSystem()
        val mounted = MountedFileSystem()
        mounted.mount("/data", inner)

        mounted.createDirectory(Path("data/subdir")).getOrThrow()
        assertTrue(inner.getMetadata(Path("subdir")).isSuccess)
    }

    @Test
    fun `delete through mount`() = testRun {
        val inner = MapFileSystem()
        inner.add("file.txt")

        val mounted = MountedFileSystem()
        mounted.mount("/", inner)

        mounted.delete(Path("file.txt")).getOrThrow()
        assertTrue(inner.getMetadata(Path("file.txt")).isFailure)
    }
}
