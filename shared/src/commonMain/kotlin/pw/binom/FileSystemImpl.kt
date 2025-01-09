package pw.binom
/*
import pw.binom.io.*
import pw.binom.io.file.File
import pw.binom.io.file.LocalFileSystem
import pw.binom.url.Path
import pw.binom.url.toPath

class FileSystemImpl : FileSystem {

    private val pool = ByteBufferPool(size = DEFAULT_BUFFER_SIZE)

    val roots = File.listRoots.map {
        val r = it.path
            .removeSuffix("\\")
            .removeSuffix(":")
        r to LocalFileSystem(root = it, byteBufferPool = pool)
    }.toMap()

    override val isSupportUserSystem: Boolean
        get() = false

    private fun getFs(path: Path): Pair<Path, FileSystem>? {
        val root = path.root
        val r = roots[root] ?: return null
        return path.raw.removeSuffix(root).toPath to r
    }

    override suspend fun get(path: Path): FileSystem.Entity? {
        if (path == "/".toPath) {
            return RootEntry(fileSystem = this, path = path)
        }
        val r = getFs(path) ?: return null
        return r.second.get(r.first)
    }

    override suspend fun getDir(path: Path): List<FileSystem.Entity>? {
        if (path == "/".toPath) {
            return roots.keys.map {
                RootEntry(fileSystem = this, path = "/$it".toPath)
            }
        }
        val r = getFs(path) ?: return null
        return r.second.getDir(r.first)
    }

    override suspend fun getQuota(path: Path): Quota? {
        if (path == "/".toPath) {
            return null
        }
        val r = getFs(path) ?: return null
        return r.second.getQuota(r.first)
    }

    override suspend fun mkdir(path: Path): FileSystem.Entity? {
        if (path == "/".toPath) {
            return null
        }
        val r = getFs(path) ?: return null
        return r.second.mkdir(r.first)
    }

    override suspend fun new(path: Path): AsyncOutput {
        if (path == "/".toPath) {
            throw IOException("Not supported")
        }
        val r = getFs(path) ?: throw FileSystem.FileNotFoundException(path)
        return r.second.new(r.first)
    }

    override suspend fun <T> useUser(user: Any?, func: suspend () -> T): T = func()

    private class RootEntry(override val fileSystem: FileSystem, override val path: Path) : FileSystem.Entity {
        override val isFile: Boolean
            get() = false
        override val lastModified: Long
            get() = 0
        override val length: Long
            get() = 0

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
