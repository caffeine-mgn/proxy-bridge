package pw.binom.proxy.services

import kotlinx.io.files.Path
import pw.binom.webdav.fs.CopyOrMoveResult
import pw.binom.webdav.fs.FileMetadata
import pw.binom.webdav.fs.WebDavFileSystem
import pw.binom.webdav.fs.mounted.MountedFileSystem

class WebDavFileSystemService : WebDavFileSystem {

    private val mountedFileSystem = MountedFileSystem()

    override suspend fun list(path: Path): Result<List<FileMetadata>> =
        mountedFileSystem.list(path)

    override suspend fun getMetadata(path: Path): Result<FileMetadata> =
        mountedFileSystem.getMetadata(path)

    override suspend fun readFile(
        path: Path,
        range: LongRange?
    ): Result<ByteArray> = mountedFileSystem.readFile(path, range)

    override suspend fun writeFile(
        path: Path,
        content: ByteArray,
        overwrite: Boolean
    ): Result<Unit> = mountedFileSystem.writeFile(path = path, content = content, overwrite = overwrite)

    override suspend fun createDirectory(path: Path): Result<Unit> =
        mountedFileSystem.createDirectory(path)

    override suspend fun delete(path: Path): Result<Unit> =
        mountedFileSystem.delete(path)

    override suspend fun move(
        source: Path,
        destination: Path
    ): Result<CopyOrMoveResult> = mountedFileSystem.move(source = source, destination = destination)

    override suspend fun copy(
        source: Path,
        destination: Path
    ): Result<CopyOrMoveResult> = mountedFileSystem.copy(source = source, destination = destination)
}
