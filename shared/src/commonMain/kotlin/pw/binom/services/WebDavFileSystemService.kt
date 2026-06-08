package pw.binom.services

import kotlinx.io.files.Path
import pw.binom.webdav.fs.CopyOrMoveResult
import pw.binom.webdav.fs.FileMetadata
import pw.binom.webdav.fs.WebDavFileSystem

class WebDavFileSystemService: WebDavFileSystem {

    override suspend fun list(path: Path): Result<List<FileMetadata>> {
        TODO("Not yet implemented")
    }

    override suspend fun getMetadata(path: Path): Result<FileMetadata> {
        TODO("Not yet implemented")
    }

    override suspend fun readFile(
        path: Path,
        range: LongRange?
    ): Result<ByteArray> {
        TODO("Not yet implemented")
    }

    override suspend fun writeFile(
        path: Path,
        content: ByteArray,
        overwrite: Boolean
    ): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun createDirectory(path: Path): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun delete(path: Path): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun move(
        source: Path,
        destination: Path
    ): Result<CopyOrMoveResult> {
        TODO("Not yet implemented")
    }

    override suspend fun copy(
        source: Path,
        destination: Path
    ): Result<CopyOrMoveResult> {
        TODO("Not yet implemented")
    }
}
