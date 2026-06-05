package pw.binom.properties

import pw.binom.multiplexer.Multiplexer
import kotlin.coroutines.CoroutineContext

class CurrentMultiplexer(val multiplexer: Multiplexer) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = Key

    object Key : CoroutineContext.Key<CurrentMultiplexer>
}
