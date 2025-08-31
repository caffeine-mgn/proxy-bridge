package pw.binom.transport

private class VirtualManagerClosing(val source: VirtualManager, val onClose: () -> Unit) : VirtualManager by source {
    override fun close() {
        try {
            source.close()
        } finally {
            onClose()
        }
    }
}

fun VirtualManager.onClose(func: () -> Unit): VirtualManager = VirtualManagerClosing(
    source = this,
    onClose = func,
)
