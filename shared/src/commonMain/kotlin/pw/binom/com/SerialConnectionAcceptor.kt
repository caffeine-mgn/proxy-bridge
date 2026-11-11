package pw.binom.com

import pw.binom.io.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pw.binom.SingleConnectAcceptor
import pw.binom.multiplexer.DuplexChannel
import pw.binom.serial.SerialConfig
import pw.binom.serial.openSerialPort

/**
 * Открывает последовательный порт через serial-kmp и отдаёт его как [DuplexChannel].
 *
 * Порт переоткрывается по требованию: после обрыва/закрытия канала следующий
 * вызов [connection] снова откроет устройство.
 */
class SerialConnectionAcceptor(
    val serialName: String,
    val config: SerialConfig = SerialConfig(baudRate = 115200, readTimeoutMs = 200),
) : SingleConnectAcceptor() {
    private val logger = KotlinLogging.logger {}

    override suspend fun createConnection(onClose: () -> Unit): DuplexChannel {
        logger.info { "Opening connection $serialName (${config.baudRate} baud)" }
        val port = withContext(ioDispatcher) {
            openSerialPort(serialName, config)
        }
        return SerialConnection.create(
            port = port,
            onClosed = onClose,
        )
    }
}
