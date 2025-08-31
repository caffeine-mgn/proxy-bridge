package pw.binom.transport

import java.io.OutputStream

class OutputStreamImpl(val delegate: OutputStream): OutputStream() {
    override fun close() {
        println("OutputStreamImpl::close closing $delegate\n${Exception().stackTraceToString()}")
        delegate.close()
    }

    override fun flush() {
        delegate.flush()
    }

    override fun write(b: ByteArray?, off: Int, len: Int) {
        delegate.write(b, off, len)
    }

    override fun write(b: ByteArray?) {
        delegate.write(b)
    }

    override fun write(b: Int) {
        delegate.write(b)
    }
}
