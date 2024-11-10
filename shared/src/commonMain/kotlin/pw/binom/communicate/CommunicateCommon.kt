package pw.binom.communicate

import pw.binom.io.AsyncCloseable
import pw.binom.io.ByteBuffer

@Deprecated(message = "Not use it")
interface CommunicateCommon : AsyncCloseable {
    fun incomeFrame(data: ByteBuffer)
    fun closeSide()
    suspend fun processing()
}
