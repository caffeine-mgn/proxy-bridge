package pw.binom.proxy.webdav

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.files.Path
import pw.binom.proxy.channel.FileChannel
import pw.binom.multiplexer.DuplexChannel
import pw.binom.multiplexer.boolean
import pw.binom.multiplexer.lebInt
import pw.binom.multiplexer.lebString
import pw.binom.multiplexer.nullable
import pw.binom.properties.OutcomeService
import pw.binom.proxy.utils.send
import pw.binom.utils.read
import pw.binom.utils.write
import pw.binom.webdav.fs.CopyOrMoveResult
import pw.binom.webdav.fs.FileMetadata
import pw.binom.webdav.fs.WebDavFileSystem

/**
 * Читает данные из [DuplexChannel] (блокирующая обёртка с [runBlocking]).
 */
private class ChannelReadSource(
    private val channel: DuplexChannel,
) : RawSource {
    private val logger = KotlinLogging.logger {}
    private var done = false
    private var chunkNum = 0

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long = runBlocking {
        if (done) return@runBlocking -1L
        val buf = channel.income.receive()
        val size = buf.lebInt()
        if (size == 0) {
            logger.info { "ChannelReadSource: EOF after ${chunkNum} chunks" }
            done = true
            return@runBlocking -1L
        }
        val data = ByteArray(size)
        var readOffset = 0
        while (readOffset < size) {
            val n = buf.readAtMostTo(data, readOffset, size)
            if (n <= 0) break
            readOffset += n
        }
        if (readOffset != size) {
            logger.error { "ChannelReadSource: chunk $chunkNum expected $size bytes, got $readOffset! TRUNCATED!" }
        }
        val zeros = data.count { it == 0.toByte() }
        val prefix = data.take(8).joinToString(" ") { "%02x".format(it) }
        logger.warn { "[ChannelReadSource] chunk=$chunkNum size=$size zeros=$zeros prefix=[$prefix]" }
        if (zeros > size / 2) {
            logger.error { "ChannelReadSource: chunk $chunkNum is ${zeros}/$size zeros! CORRUPTION!" }
        }
        chunkNum++
        sink.write(data, 0, data.size)
        data.size.toLong()
    }

    override fun close() {
        channel.cancel()
    }
}

/**
 * Пишет данные в [DuplexChannel] (блокирующая обёртка с [runBlocking]).
 */
private class ChannelWriteSink(
    private val channel: DuplexChannel,
) : RawSink {
    private var closed = false

    override fun write(source: Buffer, byteCount: Long) {
        if (closed) throw IllegalStateException("Sink closed")
        runBlocking {
            val bytes = ByteArray(byteCount.toInt())
            source.readAtMostTo(bytes, 0, bytes.size)
            channel.send {
                lebInt(bytes.size)
                write(bytes)
            }
            channel.income.receive()
            val prefix = bytes.take(8).joinToString(" ") { "%02x".format(it) }
            System.err.println("[ChannelWriteSink] wrote ${bytes.size} bytes prefix=[$prefix]")
        }
    }

    override fun flush() = Unit

    override fun close() {
        if (closed) return
        closed = true
        runBlocking {
            channel.send { lebInt(0) }
        }
    }
}

class RemoteWebDavFileSystem(
    private val outcome: OutcomeService,
    private val fileChannel: FileChannel,
    val name: String,
) : WebDavFileSystem {

    private val logger = KotlinLogging.logger {}

    private suspend inline fun <T> withChannel(func: (DuplexChannel) -> T): T {
        return outcome.createChannel().use { channel ->
            try {
                func(channel)
            } catch (e: Exception) {
                logger.error(e) { "RemoteWebDavFileSystem[$name]: error" }
                throw e
            }
        }
    }

    override suspend fun list(path: Path): Result<List<FileMetadata>> = runCatching {
        withChannel { c -> fileChannel.list(c, name, path.toString()).getOrThrow() }
    }

    override suspend fun getMetadata(path: Path): Result<FileMetadata> = runCatching {
        withChannel { c -> fileChannel.getMetadata(c, name, path.toString()).getOrThrow() }
    }

    override suspend fun readFile(path: Path, range: LongRange?): RawSource {
        val channel = outcome.createChannel()
        channel.send {
            writeByte(FileChannel.ID)
            lebString(name)
            writeByte(3) // READ_FILE
            lebString(path.toString())
            nullable(range) { it.write(this) }
        }
        val resp = channel.receive()
        if (!resp.boolean()) {
            channel.close()
            throw IllegalStateException("Remote readFile failed: $path")
        }
        return ChannelReadSource(channel)
    }

    override suspend fun writeFile(path: Path, overwrite: Boolean): RawSink {
        val channel = outcome.createChannel()
        channel.send {
            writeByte(FileChannel.ID)
            lebString(name)
            writeByte(4) // WRITE_FILE
            lebString(path.toString())
            boolean(overwrite)
        }
        return ChannelWriteSink(channel)
    }

    override suspend fun createDirectory(path: Path): Result<Unit> = runCatching {
        withChannel { c -> fileChannel.createDirectory(c, name, path.toString()).getOrThrow() }
    }

    override suspend fun delete(path: Path): Result<Unit> = runCatching {
        withChannel { c -> fileChannel.delete(c, name, path.toString()).getOrThrow() }
    }

    override suspend fun move(source: Path, destination: Path): Result<CopyOrMoveResult> = runCatching {
        withChannel { c -> fileChannel.move(c, name, source.toString(), destination.toString()).getOrThrow() }
    }

    override suspend fun copy(source: Path, destination: Path): Result<CopyOrMoveResult> = runCatching {
        withChannel { c -> fileChannel.copy(c, name, source.toString(), destination.toString()).getOrThrow() }
    }
}
