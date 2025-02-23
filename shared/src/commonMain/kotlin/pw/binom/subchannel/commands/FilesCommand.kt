package pw.binom.subchannel.commands

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import pw.binom.*
import pw.binom.config.LOCAL_FS
import pw.binom.frame.FrameChannel
import pw.binom.frame.toAsyncChannel
import pw.binom.io.*
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.serialization.PathSerializer
import pw.binom.services.ClientService
import pw.binom.strong.inject
import pw.binom.subchannel.AbstractCommandClient
import pw.binom.subchannel.Command
import pw.binom.url.Path
import kotlin.jvm.JvmInline

class FilesCommand : Command<FilesCommand.FilesClient> {

    private val logger by Logger.ofThisOrGlobal

    private val fs by inject<FileSystem2>(name = LOCAL_FS)
    private val clientService by inject<ClientService>()

    suspend fun client() = clientService.startServer(this)

    @Serializable
    sealed interface FS {

        @Serializable
        data class GetDir(@Serializable(PathSerializer::class) val path: Path) : FS

        @Serializable
        data class ReadFile(
            @Serializable(PathSerializer::class) val path: Path,
            val range: Range? = null,
        ) : FS

        @Serializable
        data class PutFile(@Serializable(PathSerializer::class) val path: Path, val override: Boolean) : FS

        @Serializable
        data class Delete(
            @Serializable(PathSerializer::class) val path: Path,
            val recursive: Boolean,
        ) : FS

        @Serializable
        data class Mkdir(@Serializable(PathSerializer::class) val path: Path) : FS

        @Serializable
        data class GetEntity(@Serializable(PathSerializer::class) val path: Path) : FS

        @Serializable
        data class Move(
            @Serializable(PathSerializer::class) val from: Path,
            @Serializable(PathSerializer::class) val to: Path,
            val overwrite: Boolean
        ) : FS

        @Serializable
        data class Copy(
            @Serializable(PathSerializer::class) val from: Path,
            @Serializable(PathSerializer::class) val to: Path,
            val overwrite: Boolean
        ) : FS
    }

    private val Range.toFs
        get() = when (this) {
            is Range.First -> FileSystem2.Range.First(start = start)
            is Range.Between -> FileSystem2.Range.Between(start = start, end = end)
            is Range.Last -> FileSystem2.Range.Last(size = size)
        }

    @Serializable
    sealed interface Range {
        @Serializable
        data class First(val start: Long) : Range

        @Serializable
        data class Between(val start: Long, val end: Long) : Range

        @Serializable
        data class Last(val size: Long) : Range
    }

    @Serializable
    data class Entity(
        @Serializable(PathSerializer::class)
        val path: Path,
        val isFile: Boolean,
        val size: Long,
        val lastModified: Long,
    )

    private inline fun <T> FSResult<T>.onOk(func: (T) -> Unit) {
        if (isOk) {
            func(getOrThrow())
        }
    }

    @Serializable
    sealed interface CommandResult {

        val isOk: Boolean
        val isNotOk
            get() = !isOk

        @Serializable
        data object OK : CommandResult {
            override val isOk: Boolean
                get() = true
        }

        @Serializable
        data class UnknownError(val error: String) : CommandResult {
            override val isOk: Boolean
                get() = false
        }

        @Serializable
        data class FSError(val error: String?) : CommandResult {
            override val isOk: Boolean
                get() = false
        }

        @Serializable
        data class EntityExist(val error: String?) : CommandResult {
            override val isOk: Boolean
                get() = false
        }

        @Serializable
        data class EntityNotFound(val error: String?) : CommandResult {
            override val isOk: Boolean
                get() = false
        }

        @Serializable
        data class Forbidden(val error: String?) : CommandResult {
            override val isOk: Boolean
                get() = false
        }
    }

    @JvmInline
    value class FSResult<T>(private val raw: Any?) {
        fun getOrThrow(): T {
            if (raw is CommandResult) {
                throw IllegalStateException()
            }
            return raw as T
        }

        fun getCommandOrThrow(): CommandResult {
            if (raw !is CommandResult) {
                throw IllegalStateException()
            }
            return raw
        }

        val isOk
            get() = raw !is CommandResult
    }

    class FilesClient(override val channel: FrameChannel) : AbstractCommandClient() {

        suspend fun readFile(path: Path, range: Range?): FSResult<AsyncInput?> {
            val stream = safeClosable {
                val stream = channel.toAsyncChannel().closeOnError()
                val r = stream.sendReceive(FS.ReadFile(path = path, range = range))
                if (r.isNotOk) {
                    stream.asyncClose()
                    return FSResult(r)
                }
                if (!stream.readBoolean()) {
                    stream.asyncClose()
                    return FSResult(null)
                }
                stream
            }

            return FSResult(object : AsyncInput {
                override val available: Available
                    get() = Available.UNKNOWN

                override suspend fun asyncClose() = stream.asyncClose()

                override suspend fun read(dest: ByteBuffer) = stream.read(dest)
            })
        }

        suspend fun putFile(path: Path, override: Boolean): FSResult<AsyncOutput> {
            val stream = safeClosable {
                val stream = channel.toAsyncChannel().closeOnError()
                val c = stream.sendReceive(FS.PutFile(path = path, override = override))
                if (c.isNotOk) {
                    return FSResult(c)
                }
                stream
            }
            return FSResult(object : AsyncOutput {
                override suspend fun asyncClose() = stream.asyncClose()

                override suspend fun flush() = stream.flush()

                override suspend fun write(data: ByteBuffer) = stream.write(data)
            })
        }

        suspend fun delete(path: Path, recursive: Boolean): CommandResult =
            channel.toAsyncChannel().useAsync {
                it.sendReceive(FS.Delete(path = path, recursive = recursive))
            }

        suspend fun getEntity(path: Path): FSResult<Entity?> =
            channel.toAsyncChannel().useAsync {
                it.sendReceive(FS.GetEntity(path = path))
                    .optionOk {
                        if (it.readBoolean()) {
                            it.readObject(Entity.serializer())
                        } else {
                            null
                        }
                    }
            }

        inline fun <T> CommandResult.optionOk(func: () -> T): FSResult<T> =
            if (this.isOk) {
                FSResult(func())
            } else {
                FSResult(this)
            }

        private suspend fun AsyncChannel.sendReceive(fs: FS): CommandResult {
            writeObject(FS.serializer(), fs)
            flush()
            return readObject(CommandResult.serializer())
        }

        suspend fun mkdir(path: Path): FSResult<Entity> =
            channel.toAsyncChannel().useAsync {
                it.sendReceive(FS.Mkdir(path = path))
                    .optionOk {
                        it.readObject(Entity.serializer())
                    }
            }

        suspend fun move(from: Path, to: Path, overwrite: Boolean) =
            channel.toAsyncChannel().useAsync {
                it.sendReceive(FS.Move(from = from, to = to, overwrite = overwrite))
            }

        suspend fun copy(from: Path, to: Path, overwrite: Boolean) =
            channel.toAsyncChannel().useAsync {
                it.sendReceive(FS.Copy(from = from, to = to, overwrite = overwrite))
            }

        suspend fun getEntries(path: Path): FSResult<List<Entity>?> =
            channel.toAsyncChannel().useAsync {
                it.sendReceive(FS.GetDir(path = path))
                    .optionOk {
                        if (it.readBoolean()) {
                            it.readObject(ListSerializer(Entity.serializer()))
                        } else {
                            null
                        }
                    }
            }
    }

    override val cmd: Byte
        get() = Command.FS

    override suspend fun startClient(channel: FrameChannel) {
        channel.toAsyncChannel().useAsync { ch ->
            when (val cmd = ch.readObject(FS.serializer())) {
                is FS.Copy -> processingOk(ch) {
                    fs.copy(
                        from = cmd.from,
                        to = cmd.to,
                        override = cmd.overwrite
                    )
                }

                is FS.Delete -> processingOk(ch) {
                    fs.delete(path = cmd.path, recursive = cmd.recursive)
                }

                is FS.GetDir -> {
                    processingOk(ch) {
                        fs.getEntries(path = cmd.path)
                            ?.map { it.toInternal }
                    }.onOk {
                        logger.info("GetDir ${cmd.path}: $it")
                        ch.writeBoolean(it != null)
                        if (it != null) {
                            ch.writeObject(ListSerializer(Entity.serializer()), it)
                        }
                    }
                }

                is FS.Mkdir -> {
                    processingOk(ch) {
                        fs.makeDirectories(path = cmd.path)
                    }.onOk {
                        ch.writeObject(Entity.serializer(), it.toInternal)
                    }
                }

                is FS.Move -> processingOk(ch) {
                    fs.move(
                        from = cmd.from,
                        to = cmd.to,
                        override = cmd.overwrite
                    )
                }

                is FS.PutFile -> processingOk(ch) {
                    fs.writeFile(
                        path = cmd.path,
                        override = cmd.override,
                    )
                }.onOk {
                    ch.copyTo(it)
                }

                is FS.ReadFile -> {
                    processingOk(ch) {
                        fs.readFile(
                            path = cmd.path,
                            range = cmd.range?.toFs ?: FileSystem2.Range.First(start = 0)
                        )
                    }.onOk {
                        ch.writeBoolean(it != null)
                        it?.copyTo(ch)
                    }
                }

                is FS.GetEntity -> {
                    processingOk(ch) {
                        fs.getEntity(cmd.path)
                    }.onOk {
                        logger.info("Return ${cmd.path} -> $it")
                        ch.writeBoolean(it != null)
                        if (it != null) {
                            ch.writeObject(
                                Entity.serializer(),
                                it.toInternal
                            )
                        }
                    }
                }
            }
        }
    }

    private val FileSystem2.Entity.toInternal
        get() = Entity(
            path = path,
            isFile = isFile,
            size = size,
            lastModified = lastModified,
        )

    private suspend inline fun <T> processingOk(channel: AsyncChannel, func: () -> T): FSResult<T> {
        val result = try {
            func()
        } catch (e: Throwable) {
            val result = when (e) {
                is FileSystem2.EntityExistException -> CommandResult.EntityExist(e.message)
                is FileSystem2.FileNotFoundException -> CommandResult.EntityNotFound(e.message)
                is FileSystem2.ForbiddenException -> CommandResult.Forbidden(e.message)
                else -> CommandResult.UnknownError(e.message ?: e.toString())
            }
            channel.writeObject(CommandResult.serializer(), result)
            println("Send response $result")
            channel.flush()
            return FSResult(result)
        }

        println("Send response ${CommandResult.OK}")
        channel.writeObject(CommandResult.serializer(), CommandResult.OK)
        channel.flush()
        return FSResult(result)
    }

    override suspend fun startServer(channel: FrameChannel): FilesClient =
        FilesClient(channel)
}
