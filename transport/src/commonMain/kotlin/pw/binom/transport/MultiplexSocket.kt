package pw.binom.transport

import pw.binom.io.AsyncInput
import pw.binom.io.AsyncOutput
import pw.binom.io.Closeable

interface MultiplexSocket : Closeable {
    val input: AsyncInput
    val output: AsyncOutput
}
