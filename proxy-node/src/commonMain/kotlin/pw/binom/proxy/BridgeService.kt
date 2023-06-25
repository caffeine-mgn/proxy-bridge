package pw.binom.proxy

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.io.http.websocket.WebSocketConnection
import kotlin.coroutines.resume

class BridgeService {

    private val serverWater = HashMap<Long, ClientConnectionWater>()
    private val clientWater = HashMap<Long, ClientConnectionWater>()

    private class ClientConnectionWater(
        val continuation: CancellableContinuation<WebSocketConnection>,
        val connection: WebSocketConnection,
    )

    suspend fun waitServer(connection: WebSocketConnection, id: Long): WebSocketConnection {
        val c = serverWater.remove(id)
        return if (c != null) {
            c.continuation.resume(connection)
            c.connection
        } else {
            suspendCancellableCoroutine {
                it.invokeOnCancellation {
                    clientWater.remove(id)
                }
                clientWater[id] = ClientConnectionWater(
                    continuation = it,
                    connection = connection
                )
            }
        }
    }

    suspend fun waitClient(id: Long, connection: WebSocketConnection): WebSocketConnection {
        val k = clientWater.remove(id)
        return if (k != null) {
            k.continuation.resume(connection)
            k.connection
        } else {
            suspendCancellableCoroutine {
                it.invokeOnCancellation {
                    serverWater.remove(id)
                }
                serverWater[id] = ClientConnectionWater(
                    continuation = it,
                    connection = connection
                )
            }
        }
    }
}