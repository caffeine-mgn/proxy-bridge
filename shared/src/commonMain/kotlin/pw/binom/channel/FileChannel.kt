package pw.binom.channel

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.selector.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.remaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.koin.core.context.GlobalContext
import org.koin.dsl.bind
import org.koin.dsl.module
import pw.binom.multiplexer.*
import pw.binom.utils.send

object FileChannel : ChannelHandler {
    private val logger = KotlinLogging.logger { }
    const val ID: Byte = 2

    val module = module {
        single { FileChannel } bind ChannelHandler::class
    }

    private const val GET_FILES: Byte = 0
    private const val GET_FILE: Byte = 1
    private const val FILE_FOUND: Byte = 1
    private const val FILE_NOT_FOUND: Byte = 2
    private const val IS_NOT_FILE: Byte = 3

    data class File(val name: String, val isFile: Boolean, val size: ULong)

    abstract class FileException : RuntimeException()
    class FileNotFoundException(val path: String) : FileException() {
        override val message: String
            get() = "File $path not found"
    }

    class IsNotAFileException(val path: String) : FileException() {
        override val message: String
            get() = "Path $path is not a file"
    }

    suspend fun getFile(path: String, out: ByteWriteChannel) {
        GlobalContext.get().get<Multiplexer>().createChannel().use { channel ->
            var data = 0L
            try {
                val e = Buffer()
                e.writeByte(ID)
                e.writeByte(GET_FILE)
                e.lebString(path)
                channel.send(e)
                val infoBuffer = channel.income.receive()
                when (infoBuffer.readByte()) {
                    IS_NOT_FILE -> throw IsNotAFileException(path)
                    FILE_NOT_FOUND -> throw FileNotFoundException(path)
                    FILE_FOUND -> {
                        val size = infoBuffer.lebULong()
                        var got = 0uL
                        println("Getting file with size $size")
                        while (got < size) {
                            val buffer = channel.income.receive()
                            data += buffer.remaining
                            got += buffer.remaining.toULong()
                            out.writePacket(buffer)
                        }
                    }
                }

            } catch (e: Throwable) {
                e.printStackTrace()
                throw e
            } finally {
                println("File was successfully ready. $data bytes")
            }
        }
    }

    suspend fun getFiles(path: String): List<File> =
        getFiles(
            multiplexer = GlobalContext.get().get<Multiplexer>(),
            path = path,
        )

    suspend fun getFiles(multiplexer: Multiplexer, path: String): List<File> =
        multiplexer.createChannel().use { channel ->
            val e = Buffer()
            e.writeByte(ID)
            e.writeByte(GET_FILES)
            e.lebString(path)
            channel.send(e)
            val buf = channel.receive()
            val size = buf.lebInt()
            val result = ArrayList<File>(size)
            for (i in 0 until size) {
                val name = buf.lebString()
                val isFile = buf.readByte() == 1.toByte()
                val size = buf.lebULong()
                result += File(
                    name = name,
                    isFile = isFile,
                    size = size
                )
            }
            result
        }

    override val id: Byte
        get() = ID

    override suspend fun income(selector: SelectorManager, channel: DuplexChannel, buffer: Buffer) {
        val cmd = buffer.readByte()
        when (cmd) {
            GET_FILES -> getFiles(buffer, channel)
            GET_FILE -> getFile(buffer, channel)
            else -> return
        }
    }

    private suspend fun getFiles(buffer: Buffer, channel: DuplexChannel) {
        val path = buffer.lebString()
        val root = java.io.File(path)
        if (!root.isDirectory) {
            logger.warn { "Directory ${root.path} is not a directory" }
            channel.send {
                lebInt(0)
            }
            return
        }
        logger.warn { "Directory ${root.path} found" }
        val list = root.listFiles()
        channel.send {
            lebInt(list.size)
            list.forEach { file ->
                lebString(file.name)
                if (file.isDirectory) {
                    writeByte(0)
                } else {
                    writeByte(1)
                }
                lebULong(list.size.toULong())
            }
        }
    }

    private suspend fun getFile(buffer: Buffer, channel: DuplexChannel) {
        val path = buffer.lebString()
        val pp = Path(path)
        val meta = SystemFileSystem.metadataOrNull(pp)
        if (meta == null) {
            channel.send {
                writeByte(FILE_NOT_FOUND)
            }
            logger.info { "File $path not found" }
            return
        }

        if (meta.isDirectory) {
            channel.send {
                writeByte(IS_NOT_FILE)
            }
            logger.info { "$path is direction" }
            return
        }
        channel.send {
            writeByte(FILE_FOUND)
            lebULong(meta.size.toULong())
        }

        coroutineScope {
            logger.info { "Reading $path" }
            var dataSize = 0L
            withContext(Dispatchers.IO) {
                SystemFileSystem.source(pp).use { source ->
                    var count = 0
                    while (true) {
                        val buf = Buffer()
                        val l = source.readAtMostTo(buf, DEFAULT_BUFFER_SIZE.toLong())
                        if (l <= 0) {
                            break
                        }
                        dataSize += l
                        count++
                        channel.send(buf)
                    }
                }
            }
            logger.info { "File suspenseful send. $dataSize bytes" }
        }
    }
}
