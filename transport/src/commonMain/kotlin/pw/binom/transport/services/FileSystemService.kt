package pw.binom.transport.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import pw.binom.transport.MultiplexSocket
import pw.binom.transport.Service
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class FileSystemService(
    val scope: CoroutineScope = GlobalScope,
    val context: CoroutineContext = EmptyCoroutineContext,
) : Service {
    companion object {
        val ID = 8
        private const val PUT_FILE: Byte = 1
        private const val GET_FILE: Byte = 2
    }

    override fun income(socket: MultiplexSocket) {

    }

}
