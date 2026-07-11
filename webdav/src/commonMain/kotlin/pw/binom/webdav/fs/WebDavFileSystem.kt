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
    /**
     * Читает файл чанками. Каждый чанк доставляется через [onChunk].
     * Размер чанка определяется реализацией (обычно 64 КБ).
     * @param range если не null — читать только указанный диапазон байт
     * @param onChunk вызывается для каждого чанка данных
     * @return Result.success(Unit) при успешном чтении всего файла
     */
    suspend fun readFile(path: Path, range: LongRange? = null, onChunk: suspend (ByteArray) -> Unit): Result<Unit>
    /**
     * Пишет файл чанками. Каждый вызов [nextChunk] возвращает чанк или null при EOF.
     * @param overwrite если false — ошибка при существующем файле
     * @param nextChunk вызывается для получения следующего чанка; null = конец
     */
    suspend fun writeFile(path: Path, overwrite: Boolean = true, nextChunk: suspend () -> ByteArray?): Result<Unit>
    suspend fun createDirectory(path: Path): Result<Unit>
    suspend fun delete(path: Path): Result<Unit>
    suspend fun move(source: Path, destination: Path): Result<CopyOrMoveResult>
    suspend fun copy(source: Path, destination: Path): Result<CopyOrMoveResult>
}
