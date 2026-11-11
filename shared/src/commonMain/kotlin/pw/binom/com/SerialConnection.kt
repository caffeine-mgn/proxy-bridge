package pw.binom.com

import pw.binom.io.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.EOFException
import kotlinx.io.buffered
import pw.binom.multiplexer.DuplexChannel
import pw.binom.multiplexer.lebULong
import pw.binom.multiplexer.readFully
import pw.binom.serial.SerialPort

/**
 * Дуплексный канал поверх открытого [SerialPort] (serial-kmp).
 *
 * Кадрирование — LEB128-длина + тело, как и у остальных транспортов проекта.
 * Чтение и запись живут в отдельных корутинах: у serial-kmp один читатель и
 * один писатель на порт, что здесь и соблюдается.
 */
class SerialConnection(
    private val connect: AutoCloseable,
    override val income: ReceiveChannel<Buffer>,
    override val outcome: SendChannel<Buffer>,
) : DuplexChannel {

    companion object {
        fun create(
            port: SerialPort,
            onClosed: () -> Unit = {},
        ): SerialConnection {
            val logger = KotlinLogging.logger("SerialConnection")
            val output = Channel<Buffer>(Channel.UNLIMITED)
            val input = Channel<Buffer>(Channel.UNLIMITED)

            val outputStream = SerialRawSink(port).buffered()
            val inputStream = SerialRawSource(port).buffered()

            val writeJob = CoroutineScope(ioDispatcher).launch(CoroutineName("Serial WRITE CHANNEL")) {
                try {
                    output.consumeEach { buffer ->
                        outputStream.lebULong(buffer.size.toULong())
                        outputStream.write(buffer, buffer.size)
                        runCatching { outputStream.flush() }
                    }
                } catch (_: Throwable) {
                    // Порт закрылся или канал отменён — выходим молча, ошибка
                    // уже видна на уровне мультиплексора.
                } finally {
                    logger.debug { "Serial WRITE CHANNEL finished" }
                }
            }
            val readJob = CoroutineScope(ioDispatcher).launch(CoroutineName("Serial READ CHANNEL")) {
                try {
                    while (isActive) {
                        try {
                            val size = inputStream.lebULong()
                            val buffer = Buffer()
                            inputStream.readFully(buffer, size.toLong())
                            input.send(buffer)
                        } catch (_: EOFException) {
                            input.close()
                            break
                        } catch (e: Throwable) {
                            logger.debug(e) { "Serial READ CHANNEL stopped" }
                            input.close()
                            break
                        }
                    }
                } finally {
                    logger.debug { "Serial READ CHANNEL finished" }
                }
            }
            return SerialConnection(
                income = input,
                outcome = output,
                connect = AutoCloseable {
                    runCatching { writeJob.cancel() }
                    runCatching { readJob.cancel() }
                    runCatching { outputStream.close() }
                    runCatching { inputStream.close() }
                    runCatching { port.close() }
                    runCatching { onClosed() }
                },
            )
        }
    }

    var isClosed = false
        private set

    override fun close() {
        try {
            connect.close()
        } finally {
            isClosed = true
        }
    }
}
