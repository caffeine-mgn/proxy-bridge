package pw.binom.logging

import kotlinx.coroutines.withContext
import pw.binom.collection.WeakReferenceMap
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.logger.Logger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.math.log

class Variables(
    val map: Map<String, String>,
) : CoroutineContext.Element {
    constructor(vararg vars: Pair<String, String>) : this(mapOf(*vars))

    override val key: CoroutineContext.Key<*>
        get() = Key

    object Key : CoroutineContext.Key<Variables>

    companion object {
        private val loggers = WeakReferenceMap<Any, Map<String, String>>()
        private val lock = SpinLock()
        fun getVariablesOfObject(logger: Any) = lock.synchronize {
            loggers[logger] ?: emptyMap()
        }

        suspend fun variables() = coroutineContext[Key]?.map ?: emptyMap()
        suspend fun <T> with(vararg vars: Pair<String, String>, func: suspend () -> T): T {
            if (vars.isEmpty()) {
                return func()
            }

            val exist = coroutineContext[Key]
            val variables = if (exist == null) {
                Variables(mapOf(*vars))
            } else {
                val r = HashMap<String, String>(exist.map)
                r += vars
                Variables(r)
            }
            return withContext(variables) {
                func()
            }
        }

        fun addVariableToLogger(logger: Any, vararg vars: Pair<String, String>) {
            lock.synchronize {
                val exist = loggers[logger]
                if (exist != null) {
                    val e = HashMap(exist)
                    e += vars
                } else {
                    loggers[logger] = mapOf(*vars)
                }
            }
        }
    }
}

fun Logger.withVariables(vararg vars: Pair<String, String>): Logger {
    Variables.addVariableToLogger(this, *vars)
    return this
}
