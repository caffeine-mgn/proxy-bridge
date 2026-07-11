package pw.binom.webdav.fs.local

import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import pw.binom.webdav.fs.CopyOrMoveResult
import pw.binom.webdav.fs.FileMetadata
import pw.binom.webdav.fs.WebDavFileSystem

private const val DEFAULT_BUFFER_SIZE = 8192L

/**
 * Wraps a [RawSource] to skip [skip] bytes at start and limit reads to [limit] bytes.
 */
internal class RangeSource(
    private val source: RawSource,
    private val skip: Long,
    private val limit: Long,
) : RawSource {
    private var remaining = limit
    private var skipped = 0L

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        if (remaining <= 0) return -1
        // skip first bytes if needed
        while (skipped < skip) {
            val buf = Buffer()
            val toRead = minOf(skip - skipped, DEFAULT_BUFFER_SIZE, byteCount)
            val read = source.readAtMostTo(buf, toRead)
            if (read <= 0) return -1
            skipped += read
        }
        val count = minOf(byteCount, remaining)
        val read = source.readAtMostTo(sink, count)
        if (read > 0) remaining -= read
        return read
    }

    override fun close() = source.close()
}

class LocalFileSystem(
    private val root: Path,
) : WebDavFileSystem {

    private fun resolve(path: Path): Path {
        return if (path.isAbsolute) {
            path
        } else {
            Path(root, path.toString())
        }
    }

    override suspend fun list(path: Path): Result<List<FileMetadata>> = runCatching {
        val resolved = resolve(path)
        println("[LocalFileSystem] list: path=$path -> resolved=$resolved")
        SystemFileSystem.list(resolved).map { child ->
            val meta = SystemFileSystem.metadataOrNull(child)
                ?: return@map toMetadata(child, kotlinx.io.files.FileMetadata())
            toMetadata(child, meta)
        }.also { children ->
            println("[LocalFileSystem] list: resolved=$resolved -> ${children.size} children")
            children.forEach { println("[LocalFileSystem] list:   ${it.path}") }
        }
    }

    override suspend fun getMetadata(path: Path): Result<FileMetadata> = runCatching {
        val resolved = resolve(path)
        println("[LocalFileSystem] getMetadata: path=$path -> resolved=$resolved")
        val meta = SystemFileSystem.metadataOrNull(resolved)
            ?: error("File not found: $resolved")
        println("[LocalFileSystem] getMetadata: found -> isDir=${meta.isDirectory}, isFile=${meta.isRegularFile}")
        toMetadata(resolved, meta)
    }

    override suspend fun readFile(path: Path, range: LongRange?): RawSource {
        val resolved = resolve(path)
        val systemSource = SystemFileSystem.source(resolved)
        return if (range != null) {
            RangeSource(systemSource, range.first, range.last - range.first + 1)
        } else {
            systemSource
        }
    }

    override suspend fun writeFile(path: Path, overwrite: Boolean): RawSink {
        val resolved = resolve(path)
        if (!overwrite && SystemFileSystem.exists(resolved)) {
            throw IllegalStateException("File already exists: $resolved")
        }
        val parent = resolved.parent
        if (parent != null) {
            SystemFileSystem.createDirectories(parent)
        }
        return SystemFileSystem.sink(resolved)
    }

    override suspend fun createDirectory(path: Path): Result<Unit> = runCatching {
        val resolved = resolve(path)
        SystemFileSystem.createDirectories(resolved)
    }

    override suspend fun delete(path: Path): Result<Unit> = runCatching {
        val resolved = resolve(path)
        deleteRecursively(resolved)
    }

    override suspend fun move(source: Path, destination: Path): Result<CopyOrMoveResult> = runCatching {
        val src = resolve(source)
        val dst = resolve(destination)
        SystemFileSystem.atomicMove(src, dst)
        CopyOrMoveResult(success = true)
    }

    override suspend fun copy(source: Path, destination: Path): Result<CopyOrMoveResult> = runCatching {
        val src = resolve(source)
        val dst = resolve(destination)
        copyRecursively(src, dst)
        CopyOrMoveResult(success = true)
    }

    private fun toMetadata(path: Path, meta: kotlinx.io.files.FileMetadata): FileMetadata {
        val relative = path.toString()
            .removePrefix(root.toString())
            .trimStart('.', '/', '\\')
            .replace('\\', '/')
        val relativePath = if (relative.isEmpty()) Path(".") else Path(relative)
        println("[LocalFileSystem] toMetadata: absolute=$path -> relative='$relative'")
        return FileMetadata(
            path = relativePath,
            isDirectory = meta.isDirectory,
            isRegularFile = meta.isRegularFile,
            size = meta.size.coerceAtLeast(0),
            lastModified = 0,
        )
    }

    private fun deleteRecursively(path: Path) {
        val meta = SystemFileSystem.metadataOrNull(path) ?: return
        if (meta.isDirectory) {
            SystemFileSystem.list(path).forEach { child ->
                deleteRecursively(child)
            }
        }
        SystemFileSystem.delete(path, mustExist = false)
    }

    private suspend fun copyRecursively(source: Path, destination: Path) {
        val meta = SystemFileSystem.metadataOrNull(source)
            ?: error("Source does not exist: $source")
        if (meta.isDirectory) {
            SystemFileSystem.createDirectories(destination)
            SystemFileSystem.list(source).forEach { child ->
                val childName = child.name
                copyRecursively(child, Path(destination, childName))
            }
        } else {
            SystemFileSystem.source(source).use { src ->
                SystemFileSystem.sink(destination).use { dst ->
                    val buf = Buffer()
                    while (true) {
                        val read = src.readAtMostTo(buf, DEFAULT_BUFFER_SIZE)
                        if (read <= 0) break
                        dst.write(buf, buf.size)
                    }
                    dst.flush()
                }
            }
        }
    }
}
