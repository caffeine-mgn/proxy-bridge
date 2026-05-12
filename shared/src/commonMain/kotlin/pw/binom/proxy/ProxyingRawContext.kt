package pw.binom.proxy

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel

interface ProxyingRawContext {
    suspend fun ok(): Pair<ByteReadChannel, ByteWriteChannel>
    suspend fun ioError()
    suspend fun timeout()
    suspend fun notAvailable()
    suspend fun connectionRefused() {
        ioError()
    }

    suspend fun noRouteToHostException() {
        ioError()
    }

    suspend fun unknownHostException() {
        ioError()
    }
}
