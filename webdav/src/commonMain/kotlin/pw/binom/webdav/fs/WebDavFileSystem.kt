package pw.binom.webdav.fs

import kotlinx.io.files.Path

data class FileMetadata(
    val path: Path,
    val isDirectory: Boolean,
    val isRegularFile: Boolean,
    val size: Long,
    val lastModified: Long,
) {
    companion object;
}

data class CopyOrMoveResult(
    val success: Boolean,
    val errorMessage: String? = null,
) {
    companion object;
}

interface WebDavFileSystem {
    suspend fun list(path: Path): Result<List<FileMetadata>>
    suspend fun getMetadata(path: Path): Result<FileMetadata>
    suspend fun readFile(path: Path, range: LongRange? = null): Result<ByteArray>
    suspend fun writeFile(path: Path, content: ByteArray, overwrite: Boolean = true): Result<Unit>
    suspend fun createDirectory(path: Path): Result<Unit>
    suspend fun delete(path: Path): Result<Unit>
    suspend fun move(source: Path, destination: Path): Result<CopyOrMoveResult>
    suspend fun copy(source: Path, destination: Path): Result<CopyOrMoveResult>
}
