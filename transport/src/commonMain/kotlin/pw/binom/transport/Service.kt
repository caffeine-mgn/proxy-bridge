package pw.binom.transport

interface Service {
    fun income(socket: MultiplexSocket)
    fun onStart(virtualManager: VirtualManager) {
        // Do nothing
    }

    fun onStop(virtualManager: VirtualManager) {
        // Do nothing
    }
}
