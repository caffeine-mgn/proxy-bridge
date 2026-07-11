package pw.binom.webdav.fs.mounted

import kotlinx.io.files.Path
import pw.binom.webdav.fs.CopyOrMoveResult
import pw.binom.webdav.fs.FileMetadata
import pw.binom.webdav.fs.WebDavFileSystem

private data class ResolvedMount(
    val fs: WebDavFileSystem,
    val relativePath: String,
)

class MountedFileSystem : WebDavFileSystem {

    private val mounts = mutableListOf<Pair<String, WebDavFileSystem>>()

    fun mount(path: String, fs: WebDavFileSystem) {
        val normalized = path.removePrefix("/").removeSuffix("/")
        mounts.removeAll { it.first == normalized }
        mounts.add(normalized to fs)
    }

    fun unmount(path: String) {
        val normalized = path.removePrefix("/").removeSuffix("/")
        mounts.removeAll { it.first == normalized }
    }

    private fun resolve(path: Path): ResolvedMount? {
        val pathStr = path.toString().removePrefix("/").removeSuffix("/")
        val normalized = if (pathStr == ".") "" else pathStr

        val matching = mounts.filter { (mountPath, _) ->
            mountPath.isEmpty() || normalized == mountPath || normalized.startsWith("$mountPath/")
        }

        val best = matching.maxByOrNull { it.first.length } ?: return null

        val (mountPath, mountedFs) = best
        val relative = when {
            mountPath.isEmpty() && normalized.isEmpty() -> "."
            mountPath.isEmpty() -> normalized
            normalized == mountPath -> "."
            else -> normalized.removePrefix("$mountPath/")
        }
        return ResolvedMount(mountedFs, relative)
    }

    override suspend fun list(path: Path): Result<List<FileMetadata>> {
        val r = resolve(path) ?: return Result.failure(IllegalArgumentException("No mount for path: $path"))
        return r.fs.list(Path(r.relativePath))
    }

    override suspend fun getMetadata(path: Path): Result<FileMetadata> {
        val r = resolve(path) ?: return Result.failure(IllegalArgumentException("No mount for path: $path"))
        return r.fs.getMetadata(Path(r.relativePath))
    }

    override suspend fun readFile(path: Path, range: LongRange?, onChunk: suspend (ByteArray) -> Unit): Result<Unit> {
        val r = resolve(path) ?: return Result.failure(IllegalArgumentException("No mount for path: $path"))
        return r.fs.readFile(Path(r.relativePath), range, onChunk)
    }

    override suspend fun writeFile(path: Path, overwrite: Boolean, nextChunk: suspend () -> ByteArray?): Result<Unit> {
        val r = resolve(path) ?: return Result.failure(IllegalArgumentException("No mount for path: $path"))
        return r.fs.writeFile(Path(r.relativePath), overwrite, nextChunk)
    }

    override suspend fun createDirectory(path: Path): Result<Unit> {
        val r = resolve(path) ?: return Result.failure(IllegalArgumentException("No mount for path: $path"))
        return r.fs.createDirectory(Path(r.relativePath))
    }

    override suspend fun delete(path: Path): Result<Unit> {
        val r = resolve(path) ?: return Result.failure(IllegalArgumentException("No mount for path: $path"))
        return r.fs.delete(Path(r.relativePath))
    }

    override suspend fun move(source: Path, destination: Path): Result<CopyOrMoveResult> {
        val src = resolve(source) ?: return Result.failure(IllegalArgumentException("No mount for source: $source"))
        val dst = resolve(destination) ?: return Result.failure(IllegalArgumentException("No mount for destination: $destination"))

        return if (src.fs === dst.fs) {
            src.fs.move(Path(src.relativePath), Path(dst.relativePath))
        } else {
            crossMountMove(src, dst)
        }
    }

    override suspend fun copy(source: Path, destination: Path): Result<CopyOrMoveResult> {
        val src = resolve(source) ?: return Result.failure(IllegalArgumentException("No mount for source: $source"))
        val dst = resolve(destination) ?: return Result.failure(IllegalArgumentException("No mount for destination: $destination"))

        return if (src.fs === dst.fs) {
            src.fs.copy(Path(src.relativePath), Path(dst.relativePath))
        } else {
            crossMountCopy(src, dst)
        }
    }

    private suspend fun crossMountMove(src: ResolvedMount, dst: ResolvedMount): Result<CopyOrMoveResult> = runCatching {
        val chunks = mutableListOf<ByteArray>()
        src.fs.readFile(Path(src.relativePath)) { chunk -> chunks.add(chunk) }.getOrThrow()
        var idx = 0
        dst.fs.writeFile(Path(dst.relativePath), overwrite = true) {
            if (idx < chunks.size) chunks[idx++] else null
        }.getOrThrow()
        src.fs.delete(Path(src.relativePath)).getOrThrow()
        CopyOrMoveResult(success = true)
    }

    private suspend fun crossMountCopy(src: ResolvedMount, dst: ResolvedMount): Result<CopyOrMoveResult> = runCatching {
        val chunks = mutableListOf<ByteArray>()
        src.fs.readFile(Path(src.relativePath)) { chunk -> chunks.add(chunk) }.getOrThrow()
        var idx = 0
        dst.fs.writeFile(Path(dst.relativePath), overwrite = true) {
            if (idx < chunks.size) chunks[idx++] else null
        }.getOrThrow()
        CopyOrMoveResult(success = true)
    }
}
