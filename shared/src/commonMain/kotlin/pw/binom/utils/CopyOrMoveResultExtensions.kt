package pw.binom.utils

import kotlinx.io.Sink
import kotlinx.io.Source
import pw.binom.multiplexer.boolean
import pw.binom.multiplexer.lebString
import pw.binom.multiplexer.nullable
import pw.binom.webdav.fs.CopyOrMoveResult

fun CopyOrMoveResult.Companion.read(source: Source): CopyOrMoveResult {
    val success = source.boolean()
    val errorMessage = source.nullable { it.lebString() }
    return CopyOrMoveResult(
        success = success,
        errorMessage = errorMessage,
    )
}

fun CopyOrMoveResult.write(destination: Sink) {
    destination.boolean(success)
    destination.nullable(errorMessage) { destination.lebString(it) }
}
