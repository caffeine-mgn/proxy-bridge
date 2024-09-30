package pw.binom.proxy

import pw.binom.io.AsyncCloseable
import pw.binom.proxy.dto.ControlEventDto
import pw.binom.proxy.dto.ControlRequestDto

interface ProxyClient : AsyncCloseable {
    suspend fun sendEvent(event: ControlEventDto)
    suspend fun receiveCommand(): ControlRequestDto
}
