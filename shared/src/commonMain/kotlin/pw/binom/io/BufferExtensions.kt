package pw.binom.io

import kotlinx.io.Buffer

inline fun buildBuffer(block: Buffer.() -> Unit): Buffer {
    val buffer = Buffer()
    block(buffer)
    return buffer
}
