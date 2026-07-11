package pw.binom.webdav.fs

import kotlinx.io.RawSink
import kotlinx.io.RawSource
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
    /**
     * Открывает файл на чтение. Возвращает [RawSource].
     * Если файл не существует — бросает исключение.
     * Если [range] указан — читает только указанный диапазон байт.
     * После чтения источника необходимо закрыть его через [RawSource.close].
     */
    suspend fun readFile(path: Path, range: LongRange? = null): RawSource
    /**
     * Открывает файл на запись. Возвращает [RawSink].
     * После завершения записи необходимо закрыть [RawSink.close].
     * @param overwrite если false — бросает исключение если файл уже существует.
     */
    suspend fun writeFile(path: Path, overwrite: Boolean = true): RawSink
    suspend fun createDirectory(path: Path): Result<Unit>
    suspend fun delete(path: Path): Result<Unit>
    suspend fun move(source: Path, destination: Path): Result<CopyOrMoveResult>
    suspend fun copy(source: Path, destination: Path): Result<CopyOrMoveResult>
}
