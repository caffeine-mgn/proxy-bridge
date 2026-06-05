package pw.binom.com

import com.fazecast.jSerialComm.SerialPort
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.readByteArray

class RawSinkSerialPort(val port: SerialPort) : RawSink {
    override fun write(source: Buffer, byteCount: Long) {
        val bytes = source.readByteArray()
        port.writeBytes(bytes, bytes.size)
    }

    override fun flush() {
        // do nothing
    }

    override fun close() {

    }
}
