package pw.binom
/*
import pw.binom.date.DateTime
import pw.binom.io.AsyncInput
import pw.binom.io.AsyncOutput
import pw.binom.io.FileSystem
import pw.binom.io.Quota
import pw.binom.strong.inject
import pw.binom.subchannel.commands.FilesCommand
import pw.binom.url.Path

class RemoteFs : FileSystem {
    private val filesCommand by inject<FilesCommand>()
    override val isSupportUserSystem: Boolean
        get() = false

    override suspend fun get(path: Path): FileSystem.Entity? {
        TODO("Not yet implemented")
    }

    override suspend fun getDir(path: Path): List<FileSystem.Entity>? {
        TODO("Not yet implemented")
    }

    override suspend fun getQuota(path: Path): Quota? {
        TODO("Not yet implemented")
    }

    override suspend fun mkdir(path: Path): FileSystem.Entity? {
        filesCommand.client().mkdir(path)
        return MyEntity(
            fileSystem = this,
            isFile = false,
            lastModified = DateTime.nowTime,
            length = 0,
            path = path
        )
    }

    override suspend fun new(path: Path): AsyncOutput {
        TODO("Not yet implemented")
    }

    override suspend fun <T> useUser(user: Any?, func: suspend () -> T): T = func()

    private class MyEntity(
        override val fileSystem: FileSystem,
        override val isFile: Boolean,
        override val lastModified: Long,
        override val length: Long,
        override val path: Path
    ) : FileSystem.Entity {
        override suspend fun copy(path: Path, overwrite: Boolean): FileSystem.Entity {
            TODO("Not yet implemented")
        }

        override suspend fun delete() {
            TODO("Not yet implemented")
        }

        override suspend fun move(path: Path, overwrite: Boolean): FileSystem.Entity {
            TODO("Not yet implemented")
        }

        override suspend fun read(offset: ULong, length: ULong?): AsyncInput? {
            TODO("Not yet implemented")
        }

        override suspend fun rewrite(): AsyncOutput {
            TODO("Not yet implemented")
        }

    }
}
*/
