package pw.binom.logging

import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.db.sqlite.SQLiteConnector
import pw.binom.io.Closeable
import pw.binom.io.file.File
import pw.binom.io.use

class SQLiteLogAppender(val file: File) : Closeable {
    companion object {
        private const val TABLE_NAME = "LOGS"
    }

    private val connect = SQLiteConnector.openFile(file)

    init {
        connect.prepareStatement(
            """
            create table if not exists $TABLE_NAME (
                id integer primary key autoincrement,
                MODULE text not null,
                METHOD text not null,
                MESSAGE text not null,
                TAGS text not null
            );
        """
        ).use { it.executeUpdate() }
    }

    private val insertStatement = connect.prepareStatement(
        """
        insert into $TABLE_NAME (MODULE,METHOD,MESSAGE,TAGS) VALUES(?,?,?,?)
    """
    )
    private val lock = SpinLock()

    fun insert(module: String, method: String, message: String, tags: Map<String, String>) {
        lock.synchronize {
            insertStatement.set(0, module)
            insertStatement.set(1, method)
            insertStatement.set(2, message)
            insertStatement.set(3, "|" + tags.entries.joinToString("|") { "${it.key}=${it.value}}" } + "|")
            insertStatement.executeUpdate()
        }
    }

    override fun close() {
        connect.close()
    }
}
