package pw.binom.utils

import kotlinx.io.Sink
import kotlinx.io.Source
import pw.binom.multiplexer.boolean
import pw.binom.multiplexer.lebLong

fun LongRange.Companion.read(source: Source): LongRange {
    val start = source.lebLong()
    val endInclusive = source.lebLong()
    return LongRange(
        start = start,
        endInclusive = endInclusive,
    )
}

fun LongRange.write(destination: Sink) {
    destination.lebLong(start)
    destination.lebLong(endInclusive)
}