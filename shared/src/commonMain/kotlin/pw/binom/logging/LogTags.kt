package pw.binom.logging

import kotlin.coroutines.CoroutineContext

class LogTags(val tags: Map<String, String>) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<LogTags>

    override val key: CoroutineContext.Key<*>
        get() = Key
}
