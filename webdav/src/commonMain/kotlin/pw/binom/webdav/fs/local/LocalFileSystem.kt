package pw.binom.webdav.fs.local

import kotlinx.io.Buffer
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import pw.binom.webdav.fs.CopyOrMoveResult
import pw.binom.webdav.fs.FileMetadata
import pw.binom.webdav.fs.WebDavFileSystem

private const val DEFAULT_BUFFER_SIZE = 8192L

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
        SystemFileSystem.list(resolved).map { child ->
            val meta = SystemFileSystem.metadataOrNull(child)
                ?: return@map toMetadata(child, kotlinx.io.files.FileMetadata())
            toMetadata(child, meta)
        }
    }

    override suspend fun getMetadata(path: Path): Result<FileMetadata> = runCatching {
        val resolved = resolve(path)
        val meta = SystemFileSystem.metadataOrNull(resolved)
            ?: error("File not found: $resolved")
        toMetadata(resolved, meta)
    }

    override suspend fun readFile(path: Path, range: LongRange?): Result<ByteArray> = runCatching {
        val resolved = resolve(path)
        val all = readAllBytes(resolved)
        if (range != null) {
            val start = range.first.coerceAtLeast(0)
            val end = range.last.coerceAtMost(all.size.toLong() - 1).coerceAtLeast(start)
            all.copyOfRange(start.toInt(), (end + 1).toInt())
        } else {
            all
        }
    }

    override suspend fun writeFile(path: Path, content: ByteArray, overwrite: Boolean): Result<Unit> = runCatching {
        val resolved = resolve(path)
        if (!overwrite && SystemFileSystem.exists(resolved)) {
            error("File already exists: $resolved")
        }
        val parent = resolved.parent
        if (parent != null) {
            SystemFileSystem.createDirectories(parent)
        }
        val buf = Buffer()
        if (content.isNotEmpty()) {
            buf.write(content, 0, content.size)
        }
        SystemFileSystem.sink(resolved).use { sink ->
            if (buf.size > 0) {
                sink.write(buf, buf.size)
            }
            sink.flush()
        }
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
        return FileMetadata(
            path = path,
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

    private fun copyRecursively(source: Path, destination: Path) {
        val meta = SystemFileSystem.metadataOrNull(source)
            ?: error("Source does not exist: $source")
        if (meta.isDirectory) {
            SystemFileSystem.createDirectories(destination)
            SystemFileSystem.list(source).forEach { child ->
                val childName = child.name
                copyRecursively(child, Path(destination, childName))
            }
        } else {
            val data = readAllBytes(source)
            val buf = Buffer()
            buf.write(data, 0, data.size)
            SystemFileSystem.sink(destination).use { sink ->
                sink.write(buf, buf.size)
            }
        }
    }

    private fun readAllBytes(path: Path): ByteArray {
        val result = Buffer()
        SystemFileSystem.source(path).use { source ->
            val buf = Buffer()
            while (true) {
                val read = source.readAtMostTo(buf, DEFAULT_BUFFER_SIZE)
                if (read <= 0) break
                result.write(buf, read)
            }
        }
        val size = result.size.toInt()
        val bytes = ByteArray(size)
        result.readAtMostTo(bytes, 0, size)
        return bytes
    }
}