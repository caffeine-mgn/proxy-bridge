package pw.binom.com

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import pw.binom.*
import pw.binom.multiplexer.DuplexChannel
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class SerialConnectionAcceptor(val serialName: String, val baudRate: Int = 115200) : SingleConnectAcceptor() {
    override suspend fun createConnection(onClose: () -> Unit): DuplexChannel {
        println("Opening connection $serialName")
        val port = SerialPort.getCommPort(serialName)
        port.baudRate = baudRate
        coroutineScope {
            withContext(Dispatchers.IO) {
                if (!port.openPort()) {
                    throw IllegalStateException("Can not open ${port.systemPortName}")
                }
            }
        }
        return SerialConnection.create(
            port = port,
            onClosed = onClose,
        )
    }
}
