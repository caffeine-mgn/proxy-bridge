package pw.binom.bluetooth

import pw.binom.*
import javax.microedition.io.Connector

object ConnectorAsync {
    suspend fun open(url: String) =
        executeCoroutineInThread {
            Connector.open(url)
        }
}
