package pw.binom.logging

import pw.binom.*

class SQLiteInternalLogger(val sql: SQLiteLogAppender) : InternalLog {
    override val enabled: Boolean
        get() = true

    override fun log(level: InternalLog.Level, file: String?, line: Int?, method: String?, text: () -> String) {
        sql.insert(
            module = file ?: "",
            method = method ?: "",
            message = text(),
            tags = emptyMap(),
        )
    }

    override fun <T> tx(func: (InternalLog.Transaction) -> T): T =
        func(InternalLog.Transaction.NULL)
}
