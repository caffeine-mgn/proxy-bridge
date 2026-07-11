package pw.binom.proxy.channel

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.io.Buffer
import kotlinx.io.files.Path
import kotlinx.io.readByteArray
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
    private val fsResolver: (String) -> WebDavFileSystem?,
) : ChannelHandler {
    companion object {
        const val ID: Byte = 2
        private const val CHUNK_SIZE = 8192L
        private const val LIST: Byte = 1
        private const val GET_METADATA: Byte = 2
        private const val READ_FILE: Byte = 3
        private const val WRITE_FILE: Byte = 4
        private const val CREATE_DIRECTORY: Byte = 5
        private const val DELETE: Byte = 6
        private const val MOVE: Byte = 7
        private const val COPY: Byte = 8
    }

    private val logger = KotlinLogging.logger {}

    override val id: Byte
        get() = ID

    private fun resolveFs(fsName: String): WebDavFileSystem {
        val fs = fsResolver(fsName)
        if (fs == null) {
            logger.error { "FileChannel.resolveFs: file system '$fsName' not found (registered: N/A)" }
            error("File system '$fsName' not found")
        }
        logger.debug { "FileChannel.resolveFs: resolved '$fsName' -> $fs" }
        return fs
    }

    suspend fun getMetadata(channel: DuplexChannel, fsName: String, path: String): Result<FileMetadata> {
        logger.info { "FileChannel.getMetadata: fsName=$fsName, path=$path" }
        channel.send {
            writeByte(ID)
            lebString(fsName)
            writeByte(GET_METADATA)
            lebString(path)
        }
        return channel.receive().use { buffer ->
            if (buffer.boolean()) {
                val meta = FileMetadata.read(buffer)
                logger.info { "FileChannel.getMetadata: success -> $meta" }
                Result.success(meta)
            } else {
                logger.warn { "FileChannel.getMetadata: remote returned failure" }
                Result.failure(IllegalStateException("Remote getMetadata failed for fs=$fsName path=$path"))
            }
        }
    }

    suspend fun list(channel: DuplexChannel, fsName: String, path: String): Result<List<FileMetadata>> {
        channel.send {
            writeByte(ID)
            lebString(fsName)
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

    suspend fun readFile(channel: DuplexChannel, fsName: String, path: String, range: LongRange?, onChunk: suspend (ByteArray) -> Unit): Result<Unit> {
        channel.send {
            writeByte(ID)
            lebString(fsName)
            writeByte(READ_FILE)
            lebString(path)
            nullable(range) { it.write(this) }
        }
        val response = channel.receive()
        if (!response.boolean()) {
            return Result.failure(IllegalStateException())
        }
        while (true) {
            val buf = channel.receive()
            val chunkSize = buf.lebInt()
            if (chunkSize == 0) break
            val data = buf.readByteArray(chunkSize)
            onChunk(data)
        }
        return Result.success(Unit)
    }

    suspend fun writeFile(channel: DuplexChannel, fsName: String, path: String, overwrite: Boolean, nextChunk: suspend () -> ByteArray?): Result<Unit> {
        channel.send {
            writeByte(ID)
            lebString(fsName)
            writeByte(WRITE_FILE)
            lebString(path)
            boolean(overwrite)
        }
        while (true) {
            val chunk = nextChunk() ?: break
            channel.send {
                lebInt(chunk.size)
                write(chunk)
            }
        }
        channel.send {
            lebInt(0)
        }
        return channel.receive().use { buffer ->
            if (buffer.boolean()) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException())
            }
        }
    }

    suspend fun createDirectory(channel: DuplexChannel, fsName: String, path: String): Result<Unit> {
        channel.send {
            writeByte(ID)
            lebString(fsName)
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

    suspend fun delete(channel: DuplexChannel, fsName: String, path: String): Result<Unit> {
        channel.send {
            writeByte(ID)
            lebString(fsName)
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

    suspend fun move(channel: DuplexChannel, fsName: String, source: String, destination: String): Result<CopyOrMoveResult> {
        channel.send {
            writeByte(ID)
            lebString(fsName)
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

    suspend fun copy(channel: DuplexChannel, fsName: String, source: String, destination: String): Result<CopyOrMoveResult> {
        channel.send {
            writeByte(ID)
            lebString(fsName)
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
        var fsName = "?"
        var cmd: Byte = -1
        try {
            fsName = buffer.lebString()
            cmd = buffer.readByte()
            logger.info { "FileChannel.income: cmd=$cmd, fsName=$fsName" }
            when (cmd) {
                LIST -> handleList(buffer, channel, fsName)
                GET_METADATA -> handleGetMetadata(buffer, channel, fsName)
                READ_FILE -> handleReadFile(buffer, channel, fsName)
                WRITE_FILE -> handleWriteFile(buffer, channel, fsName)
                CREATE_DIRECTORY -> handleCreateDirectory(buffer, channel, fsName)
                DELETE -> handleDelete(buffer, channel, fsName)
                MOVE -> handleMove(buffer, channel, fsName)
                COPY -> handleCopy(buffer, channel, fsName)
                else -> logger.error { "FileChannel.income: unknown cmd=$cmd" }
            }
        } catch (e: Exception) {
            logger.error(e) { "FileChannel.income: error processing (cmd=$cmd, fsName=$fsName)" }
            try {
                channel.send {
                    boolean(false)
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun handleList(buffer: Buffer, channel: DuplexChannel, fsName: String) {
        val fs = resolveFs(fsName)
        val path = Path(buffer.lebString())
        logger.info { "FileChannel.handleList: fs=$fsName, path=$path" }
        val result = fs.list(path)
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

    private suspend fun handleGetMetadata(buffer: Buffer, channel: DuplexChannel, fsName: String) {
        val fs = resolveFs(fsName)
        val path = kotlinx.io.files.Path(buffer.lebString())
        logger.info { "FileChannel.handleGetMetadata: fs=$fsName, path=$path" }
        val result = fs.getMetadata(path)
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

    private suspend fun handleReadFile(buffer: Buffer, channel: DuplexChannel, fsName: String) {
        val fs = resolveFs(fsName)
        val path = kotlinx.io.files.Path(buffer.lebString())
        val range = buffer.nullable { LongRange.read(it) }
        try {
            val source = fs.readFile(path, range)
            source.use { src ->
                channel.send {
                    boolean(true)
                }
                var tmp = Buffer()
                var chunkNum = 0
                while (true) {
                    val read = src.readAtMostTo(tmp, CHUNK_SIZE)
                    if (read <= 0) break
                    val size = tmp.size.toInt()
                    if (size != read.toInt()) {
                        logger.warn { "handleReadFile: chunk $chunkNum size mismatch! read=$read, tmp.size=$size" }
                    }
                    val data = ByteArray(size)
                    var readOffset = 0
                    while (readOffset < size) {
                        val n = tmp.readAtMostTo(data, readOffset, size)
                        if (n <= 0) break
                        readOffset += n
                    }
                    if (data.all { it == 0.toByte() }) {
                        logger.warn { "handleReadFile: chunk $chunkNum is ALL ZEROS! size=$size" }
                    }
                    tmp = Buffer()
                    chunkNum++
                    channel.send {
                        lebInt(size)
                        write(data)
                    }
                }
                channel.send {
                    lebInt(0)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "FileChannel.handleReadFile: error" }
            channel.send {
                boolean(false)
            }
        }
    }

    private suspend fun handleWriteFile(buffer: Buffer, channel: DuplexChannel, fsName: String) {
        val fs = resolveFs(fsName)
        val path = kotlinx.io.files.Path(buffer.lebString())
        val overwrite = buffer.boolean()
        try {
            val sink = fs.writeFile(path, overwrite)
            sink.use { s ->
                while (true) {
                    val buf = channel.receive()
                    val size = buf.lebInt()
                    if (size == 0) break
                    val data = buf.readByteArray(size)
                    val tmp = Buffer()
                    tmp.write(data, 0, size)
                    s.write(tmp, tmp.size)
                }
                s.flush()
            }
            channel.send {
                boolean(true)
            }
        } catch (e: Exception) {
            logger.error(e) { "FileChannel.handleWriteFile: error" }
            channel.send {
                boolean(false)
            }
        }
    }

    private suspend fun handleCreateDirectory(buffer: Buffer, channel: DuplexChannel, fsName: String) {
        val fs = resolveFs(fsName)
        val path = kotlinx.io.files.Path(buffer.lebString())
        val result = fs.createDirectory(path)
        channel.send {
            result.fold(
                onSuccess = { boolean(true) },
                onFailure = { boolean(false) }
            )
        }
    }

    private suspend fun handleDelete(buffer: Buffer, channel: DuplexChannel, fsName: String) {
        val fs = resolveFs(fsName)
        val path = kotlinx.io.files.Path(buffer.lebString())
        val result = fs.delete(path)
        channel.send {
            result.fold(
                onSuccess = { boolean(true) },
                onFailure = { boolean(false) }
            )
        }
    }

    private suspend fun handleMove(buffer: Buffer, channel: DuplexChannel, fsName: String) {
        val fs = resolveFs(fsName)
        val source = kotlinx.io.files.Path(buffer.lebString())
        val destination = kotlinx.io.files.Path(buffer.lebString())
        val result = fs.move(source, destination)
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

    private suspend fun handleCopy(buffer: Buffer, channel: DuplexChannel, fsName: String) {
        val fs = resolveFs(fsName)
        val source = kotlinx.io.files.Path(buffer.lebString())
        val destination = kotlinx.io.files.Path(buffer.lebString())
        val result = fs.copy(source, destination)
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
