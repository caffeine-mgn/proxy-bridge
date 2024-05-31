package pw.binom.proxy.client

import pw.binom.copyTo
import pw.binom.io.*
import pw.binom.strong.inject
import pw.binom.url.toPath

class FileService {
    private val fs by inject<FileSystem>()
    suspend fun putFile(fileDest: String, input: AsyncInput, buffer: ByteBuffer) {
        val path = fileDest.toPath
        path.parent?.let { fs.mkdirs(it) }
        fs.new(path).useAsync { file ->
            input.copyTo(dest = file, buffer = buffer)
        }
    }
}
