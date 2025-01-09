package pw.binom.config

import pw.binom.*
import pw.binom.io.file.File
import pw.binom.io.file.LocalFileSystem
import pw.binom.strong.Strong
import pw.binom.strong.bean

const val LOCAL_FS = "localFs"

fun FileSystemConfig() = Strong.config {
//    val fs = if (Environment.os == OS.WINDOWS) {
//        FileSystemImpl()
//    } else {
//        LocalFileSystem(root = File("/"), byteBufferPool = ByteBufferPool(size = DEFAULT_BUFFER_SIZE))
//    }
//    it.bean(name = "LOCAL_FS") { fs }
}
