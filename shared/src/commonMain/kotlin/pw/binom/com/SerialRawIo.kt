package pw.binom.com

import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.readByteArray
import pw.binom.serial.SerialException
import pw.binom.serial.SerialPort

/**
 * Адаптер [SerialPort] (serial-kmp) под [RawSource]/[RawSink] из kotlinx-io.
 *
 * Чтение блокирующее: [SerialPort.read] ждёт данные не дольше `readTimeoutMs`,
 * а при таймауте мы продолжаем ждать в цикле — контракт [RawSource] не допускает
 * «пустых» чтений, возвращать 0 можно, но потребители (leb128/readFully) будут
 * крутиться вхолостую, поэтому ждём до появления байт или EOF.
 */
internal class SerialRawSource(private val port: SerialPort) : RawSource {
    private val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        if (byteCount <= 0L) return 0L
        while (true) {
            val size = minOf(byteCount, buffer.size.toLong()).toInt()
            val read = try {
                port.read(buffer, 0, size)
            } catch (_: SerialException.ClosedException) {
                return -1L
            }
            if (read < 0) return -1L
            if (read == 0) continue
            sink.write(buffer, 0, read)
            return read.toLong()
        }
    }

    override fun close() {
        // Порт закрывает SerialConnection.
    }
}

internal class SerialRawSink(private val port: SerialPort) : RawSink {
    private val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    override fun write(source: Buffer, byteCount: Long) {
        var remaining = byteCount
        while (remaining > 0L) {
            val size = minOf(remaining, buffer.size.toLong()).toInt()
            val read = source.readByteArray(size)
            port.write(read, 0, read.size)
            remaining -= read.size.toLong()
        }
    }

    override fun flush() {
        // serial-kmp `flush()` — это tcflush(TCIOFLUSH)/PurgeComm, т.е. СБРОС
        // буферов, а не досылка данных. Вызывать его после записи нельзя:
        // непереданные байты будут выброшены. serial_write блокирующий и
        // отдаёт все байты драйверу, поэтому flush здесь не нужен.
    }

    override fun close() {
        // Порт закрывает SerialConnection.
    }
}

private const val DEFAULT_BUFFER_SIZE = 16 * 1024
