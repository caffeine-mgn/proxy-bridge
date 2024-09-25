package pw.binom.proxy

import pw.binom.*
import pw.binom.NullAppendable.append
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.date.DateTime
import pw.binom.date.format.toDatePattern
import pw.binom.io.bufferedWriter
import pw.binom.io.file.File
import pw.binom.io.file.openWrite
import pw.binom.thread.Thread

class GLog(val file: File) : InternalLog {
    companion object {
        private val DATE_PATTERN = "yyyyMMdd HH:mm:ss.SSSSSS".toDatePattern()
    }

    init {
        file.parent?.mkdirs()
    }

    private val writer = file.openWrite(append = true).bufferedWriter()
    private val lock = SpinLock()

    override fun log(
        level: InternalLog.Level,
        file: String?,
        line: Int?,
        method:String?,
        text: () -> String,
    ) {
        val levelStr =
            when (level) {
                InternalLog.Level.INFO -> "I"
                InternalLog.Level.WARNING -> "W"
                InternalLog.Level.ERROR -> "E"
                InternalLog.Level.CRITICAL -> "C"
                InternalLog.Level.FATAL -> "F"
            }
        val dateStr = DATE_PATTERN.toString(DateTime.now, DateTime.systemZoneOffset)
        val threadId = Thread.currentThread.id.toString()
        val lines = text().lines()
        lock.synchronize {
            lines.forEach { lineTxt ->
                writer.append(levelStr)
                    .append(dateStr)
                    .append(" ").append(threadId) // threadid
                    .append(" ${file ?: "none"}:") // file and line
                    .append(line?.toString() ?: "0")
                    .append("] ") // file and line
                    .append(lineTxt)
                    .append("\n")
            }
            writer.flush()
        }
    }
}
