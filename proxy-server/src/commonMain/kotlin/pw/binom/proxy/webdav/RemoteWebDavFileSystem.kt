package pw.binom.proxy.webdav

import kotlinx.io.files.Path
import pw.binom.proxy.channel.FileChannel
import pw.binom.multiplexer.DuplexChannel
import pw.binom.properties.OutcomeService
import pw.binom.webdav.fs.CopyOrMoveResult
import pw.binom.webdav.fs.FileMetadata
import pw.binom.webdav.fs.WebDavFileSystem

class RemoteWebDavFileSystem(
    private val outcome: OutcomeService,
    private val fileChannel: FileChannel,
) : WebDavFileSystem {

    private suspend inline fun <T> createChannel(func: (DuplexChannel) -> T) = outcome.createChannel().use { func(it) }

    override suspend fun list(path: Path): Result<List<FileMetadata>> =
        createChannel {
            fileChannel.list(it, path.toString())
        }

    override suspend fun getMetadata(path: Path): Result<FileMetadata> =
        createChannel {
            fileChannel.getMetadata(it, path.toString())
        }

    override suspend fun readFile(
        path: Path,
        range: LongRange?,
        onChunk: suspend (ByteArray) -> Unit,
    ): Result<Unit> =
        createChannel {
            fileChannel.readFile(it, path.toString(), range, onChunk)
        }

    override suspend fun writeFile(
        path: Path,
        overwrite: Boolean,
        nextChunk: suspend () -> ByteArray?,
    ): Result<Unit> =
        createChannel {
            fileChannel.writeFile(it, path.toString(), overwrite, nextChunk)
        }

    override suspend fun createDirectory(path: Path): Result<Unit> =
        createChannel {
            fileChannel.createDirectory(it, path.toString())
        }

    override suspend fun delete(path: Path): Result<Unit> =
        createChannel {
            fileChannel.delete(it, path.toString())
        }

    override suspend fun move(
        source: Path,
        destination: Path
    ): Result<CopyOrMoveResult> =
        createChannel {
            fileChannel.move(it, source.toString(), destination.toString())
        }

    override suspend fun copy(
        source: Path,
        destination: Path
    ): Result<CopyOrMoveResult> =
        createChannel {
            fileChannel.copy(it, source.toString(), destination.toString())
        }
}
