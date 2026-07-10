package pw.binom.proxy.channel

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.io.Buffer
import kotlinx.io.files.Path
import kotlinx.io.readByteArray
import org.koin.dsl.bind
import org.koin.dsl.module
import pw.binom.multiplexer.DuplexChannel
import pw.binom.multiplexer.boolean
import pw.binom.multiplexer.lebInt
import pw.binom.multiplexer.lebString
import pw.binom.multiplexer.list
import pw.binom.multiplexer.nullable
import pw.binom.utils.read
import pw.binom.proxy.utils.send
import pw.binom.utils.write
import pw.binom.webdav.fs.CopyOrMoveResult
import pw.binom.webdav.fs.FileMetadata
import pw.binom.webdav.fs.WebDavFileSystem

class FileChannel(
    private val fileSystem: WebDavFileSystem,
) : ChannelHandler {
    companion object {
        const val ID: Byte = 2
        private const val LIST: Byte = 1
        private const val GET_METADATA: Byte = 2
        private const val READ_FILE: Byte = 3
        private const val WRITE_FILE: Byte = 4
        private const val CREATE_DIRECTORY: Byte = 5
        private const val DELETE: Byte = 6
        private const val MOVE: Byte = 7
        private const val COPY: Byte = 8

        val module = module {
            single { FileChannel(get()) } bind ChannelHandler::class
        }
    }

    private val logger = KotlinLogging.logger {}

    override val id: Byte
        get() = ID

    suspend fun getMetadata(channel: DuplexChannel, path: String): Result<FileMetadata> {
        channel.send {
            writeByte(ID)
            writeByte(GET_METADATA)
            lebString(path)
        }
        return channel.receive().use { buffer ->
            if (buffer.boolean()) {
                Result.success(FileMetadata.read(buffer))
            } else {
                Result.failure(IllegalStateException())
            }
        }
    }

    suspend fun list(channel: DuplexChannel, path: String): Result<List<FileMetadata>> {
        channel.send {
            writeByte(ID)
            writeByte(LIST)
            lebString(path)
        }
        return channel.receive().use { buffer ->
            if (buffer.boolean()) {
                Result.success(buffer.list { FileMetadata.read(it) })
            } else {
                Result.failure(IllegalStateException())
            }
        }
    }

    suspend fun readFile(channel: DuplexChannel, path: String, range: LongRange?): Result<ByteArray> {
        channel.send {
            writeByte(ID)
            writeByte(READ_FILE)
            lebString(path)
            nullable(range) { it.write(this) }
        }
        return channel.receive().use { buffer ->
            if (buffer.boolean()) {
                val size = buffer.lebInt()
                Result.success(buffer.readByteArray(size))
            } else {
                Result.failure(IllegalStateException())
            }
        }
    }

    suspend fun writeFile(channel: DuplexChannel, path: String, content: ByteArray, overwrite: Boolean): Result<Unit> {
        channel.send {
            writeByte(ID)
            writeByte(WRITE_FILE)
            lebString(path)
            lebInt(content.size)
            write(content)
            boolean(overwrite)
        }
        return channel.receive().use { buffer ->
            if (buffer.boolean()) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException())
            }
        }
    }

    suspend fun createDirectory(channel: DuplexChannel, path: String): Result<Unit> {
        channel.send {
            writeByte(ID)
            writeByte(CREATE_DIRECTORY)
            lebString(path)
        }
        return channel.receive().use { buffer ->
            if (buffer.boolean()) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException())
            }
        }
    }

    suspend fun delete(channel: DuplexChannel, path: String): Result<Unit> {
        channel.send {
            writeByte(ID)
            writeByte(DELETE)
            lebString(path)
        }
        return channel.receive().use { buffer ->
            if (buffer.boolean()) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException())
            }
        }
    }

    suspend fun move(channel: DuplexChannel, source: String, destination: String): Result<CopyOrMoveResult> {
        channel.send {
            writeByte(ID)
            writeByte(MOVE)
            lebString(source)
            lebString(destination)
        }
        return channel.receive().use { buffer ->
            if (buffer.boolean()) {
                Result.success(CopyOrMoveResult.read(buffer))
            } else {
                Result.failure(IllegalStateException())
            }
        }
    }

    suspend fun copy(channel: DuplexChannel, source: String, destination: String): Result<CopyOrMoveResult> {
        channel.send {
            writeByte(ID)
            writeByte(COPY)
            lebString(source)
            lebString(destination)
        }
        return channel.receive().use { buffer ->
            if (buffer.boolean()) {
                Result.success(CopyOrMoveResult.read(buffer))
            } else {
                Result.failure(IllegalStateException())
            }
        }
    }

    override suspend fun income(channel: DuplexChannel, buffer: Buffer) {
        val cmd = buffer.readByte()
        when (cmd) {
            LIST -> handleList(buffer, channel)
            GET_METADATA -> handleGetMetadata(buffer, channel)
            READ_FILE -> handleReadFile(buffer, channel)
            WRITE_FILE -> handleWriteFile(buffer, channel)
            CREATE_DIRECTORY -> handleCreateDirectory(buffer, channel)
            DELETE -> handleDelete(buffer, channel)
            MOVE -> handleMove(buffer, channel)
            COPY -> handleCopy(buffer, channel)
        }
    }

    private suspend fun handleList(buffer: Buffer, channel: DuplexChannel) {
        val path = Path(buffer.lebString())
        val result = fileSystem.list(path)
        channel.send {
            result.fold(
                onSuccess = { list ->
                    boolean(true)
                    list(list) { it.write(this) }
                },
                onFailure = {
                    boolean(false)
                }
            )
        }
    }

    private suspend fun handleGetMetadata(buffer: Buffer, channel: DuplexChannel) {
        val path = kotlinx.io.files.Path(buffer.lebString())
        val result = fileSystem.getMetadata(path)
        channel.send {
            result.fold(
                onSuccess = { meta ->
                    boolean(true)
                    meta.write(this)
                },
                onFailure = {
                    boolean(false)
                }
            )
        }
    }

    private suspend fun handleReadFile(buffer: Buffer, channel: DuplexChannel) {
        val path = kotlinx.io.files.Path(buffer.lebString())
        val range = buffer.nullable { LongRange.read(it) }
        val result = fileSystem.readFile(path, range)
        channel.send {
            result.fold(
                onSuccess = { data ->
                    boolean(true)
                    lebInt(data.size)
                    write(data)
                },
                onFailure = {
                    boolean(false)
                }
            )
        }
    }

    private suspend fun handleWriteFile(buffer: Buffer, channel: DuplexChannel) {
        val path = kotlinx.io.files.Path(buffer.lebString())
        val size = buffer.lebInt()
        val content = buffer.readByteArray(size)
        val overwrite = buffer.boolean()
        val result = fileSystem.writeFile(path, content, overwrite)
        channel.send {
            result.fold(
                onSuccess = { boolean(true) },
                onFailure = { boolean(false) }
            )
        }
    }

    private suspend fun handleCreateDirectory(buffer: Buffer, channel: DuplexChannel) {
        val path = kotlinx.io.files.Path(buffer.lebString())
        val result = fileSystem.createDirectory(path)
        channel.send {
            result.fold(
                onSuccess = { boolean(true) },
                onFailure = { boolean(false) }
            )
        }
    }

    private suspend fun handleDelete(buffer: Buffer, channel: DuplexChannel) {
        val path = kotlinx.io.files.Path(buffer.lebString())
        val result = fileSystem.delete(path)
        channel.send {
            result.fold(
                onSuccess = { boolean(true) },
                onFailure = { boolean(false) }
            )
        }
    }

    private suspend fun handleMove(buffer: Buffer, channel: DuplexChannel) {
        val source = kotlinx.io.files.Path(buffer.lebString())
        val destination = kotlinx.io.files.Path(buffer.lebString())
        val result = fileSystem.move(source, destination)
        channel.send {
            result.fold(
                onSuccess = { res ->
                    boolean(true)
                    res.write(this)
                },
                onFailure = {
                    boolean(false)
                }
            )
        }
    }

    private suspend fun handleCopy(buffer: Buffer, channel: DuplexChannel) {
        val source = kotlinx.io.files.Path(buffer.lebString())
        val destination = kotlinx.io.files.Path(buffer.lebString())
        val result = fileSystem.copy(source, destination)
        channel.send {
            result.fold(
                onSuccess = { res ->
                    boolean(true)
                    res.write(this)
                },
                onFailure = {
                    boolean(false)
                }
            )
        }
    }
}
