package pw.binom.config

import pw.binom.fs.RemoteFileSystem
import pw.binom.io.file.File
import pw.binom.io.file.LocalFileSystem2
import pw.binom.io.file.RootLocalFileSystem
import pw.binom.properties.FileSystemProperties
import pw.binom.strong.Strong
import pw.binom.strong.bean

const val LOCAL_FS = "localFs"

fun FileSystemConfig(config: FileSystemProperties) = Strong.config {
    it.bean { RemoteFileSystem() }

    it.bean(name = LOCAL_FS) {
        if (config.root.isEmpty()) {
            RootLocalFileSystem
        } else {
            LocalFileSystem2(File(config.root))
        }
    }
}
