package pw.binom.proxy.client

import pw.binom.copyTo
import pw.binom.io.AsyncInput
import pw.binom.io.ByteBuffer
import pw.binom.io.FileSystem
import pw.binom.io.file.mkdirs
import pw.binom.io.file.parent
import pw.binom.io.use
import pw.binom.strong.inject
import pw.binom.url.toPath

class FileService {
    private val fs by inject<FileSystem>()
    suspend fun putFile(fileDest: String, input: AsyncInput, buffer: ByteBuffer) {
        val path = fileDest.toPath
        path.parent?.let { fs.mkdirs(it) }
        fs.new(path).use { file ->
            input.copyTo(dest = file, buffer = buffer)
        }
    }
}
