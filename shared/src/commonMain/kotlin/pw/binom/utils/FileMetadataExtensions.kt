package pw.binom.utils

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.files.Path
import pw.binom.multiplexer.boolean
import pw.binom.multiplexer.lebLong
import pw.binom.multiplexer.lebString
import pw.binom.webdav.fs.FileMetadata

fun FileMetadata.Companion.read(source: Source): FileMetadata {
    val path = Path(source.lebString())
    val isDirectory = source.boolean()
    val isRegularFile = source.boolean()
    val size = source.lebLong()
    val lastModified = source.lebLong()
    return FileMetadata(
        path = path,
        isDirectory = isDirectory,
        isRegularFile = isRegularFile,
        size = size,
        lastModified = lastModified,
    )
}

fun FileMetadata.write(destination: Sink) {
    destination.lebString(path.toString())
    destination.boolean(isDirectory)
    destination.boolean(isRegularFile)
    destination.lebLong(size)
    destination.lebLong(lastModified)
}
