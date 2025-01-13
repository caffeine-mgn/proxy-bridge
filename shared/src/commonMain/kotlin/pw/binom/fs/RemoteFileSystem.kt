package pw.binom.fs

import pw.binom.io.AsyncInput
import pw.binom.io.AsyncOutput
import pw.binom.io.FileSystem2
import pw.binom.strong.inject
import pw.binom.subchannel.commands.FilesCommand
import pw.binom.url.Path

class RemoteFileSystem : FileSystem2 {

    private val filesCommand by inject<FilesCommand>()

    private inner class EntityImpl(e: FilesCommand.Entity) : FileSystem2.Entity {
        override val fileSystem: FileSystem2
            get() = this@RemoteFileSystem
        override val isFile: Boolean = e.isFile
        override val lastModified: Long = e.lastModified
        override val path: Path = e.path
        override val size: Long = e.size
    }

    override suspend fun appendFile(path: Path): AsyncOutput {
        TODO("Not yet implemented")
    }

    private fun FilesCommand.CommandResult.check() {
        when (this) {
            is FilesCommand.CommandResult.EntityExist -> throw FileSystem2.EntityExistException(error)
            is FilesCommand.CommandResult.EntityNotFound -> throw FileSystem2.FileNotFoundException(error)
            is FilesCommand.CommandResult.FSError -> throw FileSystem2.FSException(error)
            is FilesCommand.CommandResult.Forbidden -> throw FileSystem2.ForbiddenException(error)
            FilesCommand.CommandResult.OK -> {
                // Do nothing
            }

            is FilesCommand.CommandResult.UnknownError -> throw RuntimeException(error)
        }
    }

    override suspend fun delete(path: Path, recursive: Boolean) {
        filesCommand.client().delete(
            path = path,
            recursive = recursive,
        ).check()
    }

    override suspend fun getEntity(path: Path): FileSystem2.Entity? =
        filesCommand.client().getEntity(path)
            .checkAndGet()
            ?.let { EntityImpl(it) }

    override suspend fun getEntries(path: Path): List<FileSystem2.Entity>? =
        filesCommand.client()
            .getEntries(path)
            .checkAndGet()
            ?.map { EntityImpl(it) }

    override suspend fun getQuota(path: Path): FileSystem2.Quota? {
        TODO("Not yet implemented")
    }

    private fun <T> FilesCommand.FSResult<T>.checkAndGet(): T {
        if (isOk) {
            return getOrThrow()
        }
        val cmd = getCommandOrThrow()
        cmd.check()
        throw IllegalStateException()
    }

    override suspend fun makeDirectories(path: Path): FileSystem2.Entity =
        filesCommand.client().mkdir(path)
            .checkAndGet()
            .let { EntityImpl(it) }

    private val FileSystem2.Range.toInternal
        get() = when (this) {
            is FileSystem2.Range.First -> FilesCommand.Range.First(start = start)
            is FileSystem2.Range.Between -> FilesCommand.Range.Between(start = start, end = end)
            is FileSystem2.Range.Last -> FilesCommand.Range.Last(size = size)
        }

    override suspend fun copy(from: Path, to: Path, override: Boolean) {
        filesCommand.client().copy(
            from = from,
            to = to,
            overwrite = override
        ).check()
    }

    override suspend fun move(from: Path, to: Path, override: Boolean) {
        filesCommand.client().move(
            from = from,
            to = to,
            overwrite = override
        ).check()
    }

    override suspend fun readFile(path: Path, range: FileSystem2.Range): AsyncInput? =
        filesCommand.client().readFile(
            path = path,
            range = range.toInternal
        ).checkAndGet()

    override suspend fun writeFile(path: Path, override: Boolean): AsyncOutput =
        filesCommand.client().putFile(
            path = path,
            override = override,
        ).checkAndGet()
}
