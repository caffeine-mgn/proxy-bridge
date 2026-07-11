package pw.binom.webdav.fs.mounted

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
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

    override suspend fun readFile(path: Path, range: LongRange?): RawSource {
        val key = normalizeKey(path.toString())
        val content = files[key] ?: error("Not found: ${path}")
        val data = if (range != null) {
            val start = range.first.coerceAtLeast(0)
            val end = range.last.coerceAtMost(content.size.toLong() - 1).coerceAtLeast(start)
            content.copyOfRange(start.toInt(), (end + 1).toInt())
        } else content
        val buf = Buffer()
        buf.write(data, 0, data.size)
        return object : RawSource {
            override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
                val prev = buf.size
                if (prev <= 0) return -1
                val count = minOf(byteCount, prev)
                sink.write(buf, count)
                return count
            }
            override fun close() {}
        }
    }

    override suspend fun writeFile(path: Path, overwrite: Boolean): RawSink {
        val key = normalizeKey(path.toString())
        if (!overwrite && files.containsKey(key)) error("File exists: $path")
        val buf = Buffer()
        return object : RawSink {
            override fun write(source: Buffer, byteCount: Long) {
                buf.write(source, byteCount)
            }
            override fun flush() {}
            override fun close() {
                val size = buf.size.toInt()
                val data = ByteArray(size)
                buf.readAtMostTo(data, 0, size)
                files[key] = data
            }
        }
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
        val source = fs.readFile(path, range)
        val buf = Buffer()
        while (true) {
            val read = source.readAtMostTo(buf, 8192)
            if (read <= 0) break
        }
        val size = buf.size.toInt()
        val data = ByteArray(size)
        buf.readAtMostTo(data, 0, size)
        source.close()
        return data
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

    @Test
    fun `root getMetadata returns virtual directory`() = testRun {
        val inner = MapFileSystem()
        val mounted = MountedFileSystem()
        mounted.mount("/data", inner)

        val meta = mounted.getMetadata(Path(".")).getOrThrow()
        assertTrue(meta.isDirectory)
        assertFalse(meta.isRegularFile)
        assertEquals(Path("."), meta.path)
    }

    @Test
    fun `root list returns mount points as directories`() = testRun {
        val fsA = MapFileSystem()
        val fsB = MapFileSystem()
        val mounted = MountedFileSystem()
        mounted.mount("/a", fsA)
        mounted.mount("/b", fsB)

        val entries = mounted.list(Path(".")).getOrThrow()
        assertEquals(2, entries.size)
        assertTrue(entries.any { it.path == Path("a") && it.isDirectory })
        assertTrue(entries.any { it.path == Path("b") && it.isDirectory })
    }

    @Test
    fun `root list with empty mounts returns empty`() = testRun {
        val mounted = MountedFileSystem()
        val entries = mounted.list(Path(".")).getOrThrow()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `root list deduplicates mount points`() = testRun {
        val fsA = MapFileSystem()
        val fsB = MapFileSystem()
        val mounted = MountedFileSystem()
        mounted.mount("/a/x", fsA)
        mounted.mount("/a/y", fsB)

        val entries = mounted.list(Path(".")).getOrThrow()
        assertEquals(1, entries.size)
        assertEquals(Path("a"), entries[0].path)
    }

    @Test
    fun `root list with single mount`() = testRun {
        val inner = MapFileSystem()
        val mounted = MountedFileSystem()
        mounted.mount("/data", inner)

        val entries = mounted.list(Path(".")).getOrThrow()
        assertEquals(1, entries.size)
        assertEquals(Path("data"), entries[0].path)
    }

    @Test
    fun `sub path delegates correctly after root aggregation`() = testRun {
        val inner = MapFileSystem()
        inner.add("nested/file.txt", "content".encodeToByteArray())

        val mounted = MountedFileSystem()
        mounted.mount("/mnt", inner)

        val entries = mounted.list(Path("mnt/nested")).getOrThrow()
        assertEquals(1, entries.size)
        assertTrue(entries[0].path.toString().endsWith("file.txt"))
    }

    @Test
    fun `root mount resolves correctly`() = testRun {
        val inner = MapFileSystem()
        inner.add("root.txt", "root".encodeToByteArray())

        val mounted = MountedFileSystem()
        mounted.mount("/", inner)

        val meta = mounted.getMetadata(Path("root.txt")).getOrThrow()
        assertTrue(meta.isRegularFile)
        assertEquals("root", readAll(mounted, Path("root.txt")).decodeToString())
    }

    @Test
    fun `root mount at slash shows inner fs files`() = testRun {
        val inner = MapFileSystem()
        inner.add("file1.txt", "one".encodeToByteArray())
        inner.add("file2.txt", "two".encodeToByteArray())
        inner.add("sub", byteArrayOf()) // directory marker
        inner.add("sub/file3.txt", "three".encodeToByteArray())

        val mounted = MountedFileSystem()
        mounted.mount("/", inner)

        val entries = mounted.list(Path(".")).getOrThrow()
        assertEquals(3, entries.size) // file1.txt, file2.txt, sub (dir)
        assertTrue(entries.any { it.path.toString().endsWith("file1.txt") })
        assertTrue(entries.any { it.path.toString().endsWith("file2.txt") })
        assertTrue(entries.any { it.path.toString().endsWith("sub") && it.isDirectory })
    }

    @Test
    fun `root mount with additional mount points`() = testRun {
        val rootFs = MapFileSystem()
        rootFs.add("root.txt", "root".encodeToByteArray())
        val extraFs = MapFileSystem()
        extraFs.add("extra.txt", "extra".encodeToByteArray())

        val mounted = MountedFileSystem()
        mounted.mount("/", rootFs)
        mounted.mount("/extra", extraFs)

        val entries = mounted.list(Path(".")).getOrThrow()
        assertEquals(2, entries.size) // root.txt + extra (dir)
        assertTrue(entries.any { it.path.toString().endsWith("root.txt") })
        assertTrue(entries.any { it.path.toString().endsWith("extra") && it.isDirectory })
    }
}
