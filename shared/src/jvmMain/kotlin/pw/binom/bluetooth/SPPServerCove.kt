package pw.binom.bluetooth

import pw.binom.io.BluetoothConnection
import pw.binom.utils.executeCoroutineInThread
import javax.microedition.io.Connector
import javax.microedition.io.StreamConnectionNotifier

class SPPServerCove : SPPServer {
    private val uuid = "0000110100002000800000805f9b34fb"
    private val url = "btspp://localhost:$uuid;name=SPPServer"
    private val streamConnectionNotifier = Connector.open(url) as StreamConnectionNotifier

    suspend fun StreamConnectionNotifier.asyncAcceptAndOpen() =
        executeCoroutineInThread {
            acceptAndOpen()
        }


    override suspend fun accept(): BluetoothConnection =
        BluetoothConnection.create(streamConnectionNotifier.asyncAcceptAndOpen())

    override fun close() {
        streamConnectionNotifier.close()
    }
}
