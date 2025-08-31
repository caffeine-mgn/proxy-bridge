package pw.binom.transport

import java.io.InputStream
import java.io.OutputStream

class InputStreamImpl(val delegate: InputStream) : InputStream() {
    override fun read(): Int = delegate.read()

    override fun read(b: ByteArray?): Int {
        return delegate.read(b)
    }

    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        return delegate.read(b, off, len)
    }

    override fun readAllBytes(): ByteArray? {
        return delegate.readAllBytes()
    }

    override fun readNBytes(len: Int): ByteArray? {
        return delegate.readNBytes(len)
    }

    override fun readNBytes(b: ByteArray?, off: Int, len: Int): Int {
        return delegate.readNBytes(b, off, len)
    }

    override fun skip(n: Long): Long {
        return delegate.skip(n)
    }

    override fun skipNBytes(n: Long) {
        delegate.skipNBytes(n)
    }

    override fun available(): Int {
        return delegate.available()
    }

    override fun close() {
        println("InputStreamImpl::close closing $delegate\n${Exception().stackTraceToString()}")
        delegate.close()
    }

    override fun mark(readlimit: Int) {
        delegate.mark(readlimit)
    }

    override fun reset() {
        delegate.reset()
    }

    override fun markSupported(): Boolean {
        return delegate.markSupported()
    }

    override fun transferTo(out: OutputStream?): Long {
        return delegate.transferTo(out)
    }
}
