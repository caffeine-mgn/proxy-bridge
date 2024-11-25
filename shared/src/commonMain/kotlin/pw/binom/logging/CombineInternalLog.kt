package pw.binom.logging

import pw.binom.*

class CombineInternalLog(val a: InternalLog, val b: InternalLog) : InternalLog {
    override val enabled: Boolean
        get() = a.enabled || b.enabled

    override fun log(level: InternalLog.Level, file: String?, line: Int?, method: String?, text: () -> String) {
        a.log(level = level, file = file, line = line, method = method, text = text)
        b.log(level = level, file = file, line = line, method = method, text = text)
    }

    override fun <T> tx(func: (InternalLog.Transaction) -> T): T =
        a.tx {
            b.tx(func)
        }
}

operator fun InternalLog.plus(other: InternalLog): InternalLog =
    when {
        !enabled && !other.enabled -> InternalLog.NULL
        !enabled && other.enabled -> other
        enabled && !other.enabled -> this
        this === InternalLog.NULL -> other
        other === InternalLog.NULL -> this
        else -> CombineInternalLog(a = this, b = other)
    }
