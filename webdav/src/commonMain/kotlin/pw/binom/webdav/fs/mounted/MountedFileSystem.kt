package pw.binom.webdav.fs.mounted

import kotlinx.io.files.Path
import pw.binom.webdav.fs.CopyOrMoveResult
import pw.binom.webdav.fs.FileMetadata
import pw.binom.webdav.fs.WebDavFileSystem

private data class ResolvedMount(
    val fs: WebDavFileSystem,
    val relativePath: Path,
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

        val best = mounts.maxByOrNull { (mountPath, _) ->
            when {
                mountPath.isEmpty() -> if (normalized.isEmpty()) 0 else -1
                normalized == mountPath -> mountPath.length
                normalized.startsWith("$mountPath/") -> mountPath.length
                else -> -1
            }
        } ?: return null

        val (mountPath, mountedFs) = best
        val relative = when {
            mountPath.isEmpty() -> "/"
            normalized == mountPath -> "/"
            else -> "/" + normalized.removePrefix("$mountPath/")
        }
        return ResolvedMount(mountedFs, Path(relative))
    }

    override suspend fun list(path: Path): Result<List<FileMetadata>> {
        val r = resolve(path) ?: return Result.failure(IllegalArgumentException("No mount for path: $path"))
        return r.fs.list(r.relativePath)
    }

    override suspend fun getMetadata(path: Path): Result<FileMetadata> {
        val r = resolve(path) ?: return Result.failure(IllegalArgumentException("No mount for path: $path"))
        return r.fs.getMetadata(r.relativePath)
    }

    override suspend fun readFile(path: Path, range: LongRange?): Result<ByteArray> {
        val r = resolve(path) ?: return Result.failure(IllegalArgumentException("No mount for path: $path"))
        return r.fs.readFile(r.relativePath, range)
    }

    override suspend fun writeFile(path: Path, content: ByteArray, overwrite: Boolean): Result<Unit> {
        val r = resolve(path) ?: return Result.failure(IllegalArgumentException("No mount for path: $path"))
        return r.fs.writeFile(r.relativePath, content, overwrite)
    }

    override suspend fun createDirectory(path: Path): Result<Unit> {
        val r = resolve(path) ?: return Result.failure(IllegalArgumentException("No mount for path: $path"))
        return r.fs.createDirectory(r.relativePath)
    }

    override suspend fun delete(path: Path): Result<Unit> {
        val r = resolve(path) ?: return Result.failure(IllegalArgumentException("No mount for path: $path"))
        return r.fs.delete(r.relativePath)
    }

    override suspend fun move(source: Path, destination: Path): Result<CopyOrMoveResult> {
        val src = resolve(source) ?: return Result.failure(IllegalArgumentException("No mount for source: $source"))
        val dst = resolve(destination) ?: return Result.failure(IllegalArgumentException("No mount for destination: $destination"))

        return if (src.fs === dst.fs) {
            src.fs.move(src.relativePath, dst.relativePath)
        } else {
            crossMountMove(src, dst)
        }
    }

    override suspend fun copy(source: Path, destination: Path): Result<CopyOrMoveResult> {
        val src = resolve(source) ?: return Result.failure(IllegalArgumentException("No mount for source: $source"))
        val dst = resolve(destination) ?: return Result.failure(IllegalArgumentException("No mount for destination: $destination"))

        return if (src.fs === dst.fs) {
            src.fs.copy(src.relativePath, dst.relativePath)
        } else {
            crossMountCopy(src, dst)
        }
    }

    private suspend fun crossMountMove(src: ResolvedMount, dst: ResolvedMount): Result<CopyOrMoveResult> = runCatching {
        val data = src.fs.readFile(src.relativePath).getOrThrow()
        dst.fs.writeFile(dst.relativePath, data, overwrite = true).getOrThrow()
        src.fs.delete(src.relativePath).getOrThrow()
        CopyOrMoveResult(success = true)
    }

    private suspend fun crossMountCopy(src: ResolvedMount, dst: ResolvedMount): Result<CopyOrMoveResult> = runCatching {
        val data = src.fs.readFile(src.relativePath).getOrThrow()
        dst.fs.writeFile(dst.relativePath, data, overwrite = true).getOrThrow()
        CopyOrMoveResult(success = true)
    }
}