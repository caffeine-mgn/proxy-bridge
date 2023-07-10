package pw.binom.proxy.node

import pw.binom.io.AsyncInput

class PoolService {
    suspend fun inputReady(id: Int, channel: AsyncInput) {
    }

    suspend fun outputReady(id: Int, channel: AsyncInput) {
    }
}
