package pw.binom.com

import com.fazecast.jSerialComm.SerialPort
import kotlinx.io.Buffer
import kotlinx.io.RawSource

class RawSourceSerialPort(val port: SerialPort) : RawSource {
    init {
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0)
    }

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        val bytesAvailable = port.bytesAvailable()
        if (bytesAvailable <= 0) {
            val bytesForRead = byteCount.toInt()
            val data = ByteArray(bytesForRead)
            val wasRead = port.readBytes(data, bytesForRead, 0)
            if (wasRead == -1) {
                return -1L
            }
            sink.write(data, 0, wasRead)
            return wasRead.toLong()
        }

        val buffer = ByteArray(bytesAvailable)
        val bytesRead = port.readBytes(buffer, bytesAvailable)
        sink.write(buffer)
        return bytesRead.toLong()
    }

    override fun close() {
    }
}
