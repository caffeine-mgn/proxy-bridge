package pw.binom.logging

import pw.binom.*

class FilterInternalLog(val other: InternalLog, val filter: Filter) : InternalLog {
    interface Filter {
        fun predicat(level: InternalLog.Level, file: String?, line: Int?, method: String?, text: () -> String): Boolean
    }

    override val enabled: Boolean
        get() = other.enabled

    override fun log(level: InternalLog.Level, file: String?, line: Int?, method: String?, text: () -> String) {
        val text2 by lazy { text() }
        if (filter.predicat(level = level, file = file, line = line, method = method, text = { text2 })) {
            other.log(level = level, file = file, line = line, method = method, text = { text2 })
        }
    }

    override fun <T> tx(func: (InternalLog.Transaction) -> T): T =
        other.tx(func)
}

fun InternalLog.filter(predict: FilterInternalLog.Filter): InternalLog =
    when {
        this === InternalLog.NULL -> InternalLog.NULL
        !enabled -> InternalLog.NULL
        else -> FilterInternalLog(other = this, filter = predict)
    }
