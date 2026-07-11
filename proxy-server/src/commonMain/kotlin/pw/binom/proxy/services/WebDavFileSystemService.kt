package pw.binom.proxy.services

import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.files.Path
import pw.binom.webdav.fs.CopyOrMoveResult
import pw.binom.webdav.fs.FileMetadata
import pw.binom.webdav.fs.WebDavFileSystem

class WebDavFileSystemService : WebDavFileSystem {

    private var delegate: WebDavFileSystem? = null

    fun setDelegate(fs: WebDavFileSystem) {
        delegate = fs
    }

    override suspend fun list(path: Path): Result<List<FileMetadata>> =
        delegate?.list(path) ?: Result.failure(IllegalStateException("No delegate set"))

    override suspend fun getMetadata(path: Path): Result<FileMetadata> =
        delegate?.getMetadata(path) ?: Result.failure(IllegalStateException("No delegate set"))

    override suspend fun readFile(path: Path, range: LongRange?): RawSource =
        delegate?.readFile(path, range) ?: throw IllegalStateException("No delegate set")

    override suspend fun writeFile(path: Path, overwrite: Boolean): RawSink =
        delegate?.writeFile(path, overwrite) ?: throw IllegalStateException("No delegate set")

    override suspend fun createDirectory(path: Path): Result<Unit> =
        delegate?.createDirectory(path) ?: Result.failure(IllegalStateException("No delegate set"))

    override suspend fun delete(path: Path): Result<Unit> =
        delegate?.delete(path) ?: Result.failure(IllegalStateException("No delegate set"))

    override suspend fun move(source: Path, destination: Path): Result<CopyOrMoveResult> =
        delegate?.move(source, destination) ?: Result.failure(IllegalStateException("No delegate set"))

    override suspend fun copy(source: Path, destination: Path): Result<CopyOrMoveResult> =
        delegate?.copy(source, destination) ?: Result.failure(IllegalStateException("No delegate set"))
}
