package pw.binom.webdav.fs.mounted

import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import org.junit.Test
import pw.binom.webdav.fs.CopyOrMoveResult
import pw.binom.webdav.fs.FileMetadata
import pw.binom.webdav.fs.WebDavFileSystem
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    override suspend fun readFile(path: Path, range: LongRange?): Result<ByteArray> = runCatching {
        val key = normalizeKey(path.toString())
        val content = files[key] ?: error("Not found: ${path}")
        if (range != null) {
            val start = range.first.coerceAtLeast(0)
            val end = range.last.coerceAtMost(content.size.toLong() - 1).coerceAtLeast(start)
            content.copyOfRange(start.toInt(), (end + 1).toInt())
        } else content
    }

    override suspend fun writeFile(path: Path, content: ByteArray, overwrite: Boolean): Result<Unit> = runCatching {
        val key = normalizeKey(path.toString())
        if (!overwrite && files.containsKey(key)) error("File exists: $path")
        files[key] = content
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

    @Test
    fun `single mount routes paths correctly`() = runBlocking {
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
    fun `two mounts isolate files`() = runBlocking {
        val fsA = MapFileSystem()
        val fsB = MapFileSystem()
        fsA.add("file.txt", "AAA".encodeToByteArray())
        fsB.add("file.txt", "BBB".encodeToByteArray())

        val mounted = MountedFileSystem()
        mounted.mount("/a", fsA)
        mounted.mount("/b", fsB)

        val fromA = mounted.readFile(Path("a/file.txt")).getOrThrow()
        val fromB = mounted.readFile(Path("b/file.txt")).getOrThrow()
        assertEquals("AAA", fromA.decodeToString())
        assertEquals("BBB", fromB.decodeToString())
    }

    @Test
    fun `root mount as fallback`() = runBlocking {
        val specific = MapFileSystem()
        specific.add("data.txt", "specific".encodeToByteArray())
        val fallback = MapFileSystem()
        fallback.add("other.txt", "fallback".encodeToByteArray())

        val mounted = MountedFileSystem()
        mounted.mount("/mnt", specific)
        mounted.mount("/", fallback)

        val fromSpecific = mounted.readFile(Path("mnt/data.txt")).getOrThrow()
        val fromFallback = mounted.readFile(Path("other.txt")).getOrThrow()
        assertEquals("specific", fromSpecific.decodeToString())
        assertEquals("fallback", fromFallback.decodeToString())
    }

@Test
    fun `priority by specificity`() = runBlocking {
        val general = MapFileSystem()
        general.add("file.txt", "general".encodeToByteArray())
        val specific = MapFileSystem()
        specific.add("file.txt", "specific".encodeToByteArray())

        val mounted = MountedFileSystem()
        mounted.mount("/base", general)
        mounted.mount("/base/nested", specific)

        val fromGeneral = mounted.readFile(Path("base/file.txt")).getOrThrow()
        val fromSpecific = mounted.readFile(Path("base/nested/file.txt")).getOrThrow()
        assertEquals("general", fromGeneral.decodeToString())
        assertEquals("specific", fromSpecific.decodeToString())
    }

    @Test
    fun `unmount removes route`() = runBlocking {
        val inner = MapFileSystem()
        inner.add("test.txt", "data".encodeToByteArray())

        val mounted = MountedFileSystem()
        mounted.mount("/tmp", inner)
        assertTrue(mounted.getMetadata(Path("tmp/test.txt")).isSuccess)

        mounted.unmount("/tmp")
        assertTrue(mounted.getMetadata(Path("tmp/test.txt")).isFailure)
    }

    @Test
    fun `cross mount copy`() = runBlocking {
        val fsA = MapFileSystem()
        val fsB = MapFileSystem()
        fsA.add("src.txt", "hello".encodeToByteArray())

        val mounted = MountedFileSystem()
        mounted.mount("/a", fsA)
        mounted.mount("/b", fsB)

        val result = mounted.copy(Path("a/src.txt"), Path("b/dst.txt")).getOrThrow()
        assertTrue(result.success)

        assertEquals("hello", fsB.readFile(Path("dst.txt")).getOrThrow().decodeToString())
        assertEquals("hello", fsA.readFile(Path("src.txt")).getOrThrow().decodeToString()) // source intact
    }

    @Test
    fun `cross mount move`() = runBlocking {
        val fsA = MapFileSystem()
        val fsB = MapFileSystem()
        fsA.add("src.txt", "hello".encodeToByteArray())

        val mounted = MountedFileSystem()
        mounted.mount("/a", fsA)
        mounted.mount("/b", fsB)

        val result = mounted.move(Path("a/src.txt"), Path("b/dst.txt")).getOrThrow()
        assertTrue(result.success)

        assertEquals("hello", fsB.readFile(Path("dst.txt")).getOrThrow().decodeToString())
        assertTrue(fsA.getMetadata(Path("src.txt")).isFailure) // source deleted
    }

    @Test
    fun `same mount move uses direct delegation`() = runBlocking {
        val inner = MapFileSystem()
        inner.add("src.txt", "data".encodeToByteArray())

        val mounted = MountedFileSystem()
        mounted.mount("/", inner)

        mounted.move(Path("src.txt"), Path("dst.txt")).getOrThrow()
        assertTrue(inner.getMetadata(Path("src.txt")).isFailure)
        assertTrue(inner.getMetadata(Path("dst.txt")).isSuccess)
    }

    @Test
    fun `createDirectory delegates properly`() = runBlocking {
        val inner = MapFileSystem()
        val mounted = MountedFileSystem()
        mounted.mount("/data", inner)

        mounted.createDirectory(Path("data/subdir")).getOrThrow()
        assertTrue(inner.getMetadata(Path("subdir")).isSuccess)
    }

    @Test
    fun `delete through mount`() = runBlocking {
        val inner = MapFileSystem()
        inner.add("file.txt")

        val mounted = MountedFileSystem()
        mounted.mount("/", inner)

        mounted.delete(Path("file.txt")).getOrThrow()
        assertTrue(inner.getMetadata(Path("file.txt")).isFailure)
    }
}
