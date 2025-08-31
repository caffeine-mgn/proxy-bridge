package pw.binom.logging

import pw.binom.Environment
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.bufferedWriter
import pw.binom.io.file.openWrite
import pw.binom.io.file.workDirectoryFile
import pw.binom.io.use

class FileLogSender(fileName: String) : LogSender {
    private val logFile = Environment.workDirectoryFile.relative(fileName)
    private val lock = SpinLock()
    override fun send(
        tags: Map<String, String>,
        message: String?,
        exception: Throwable?,
        loggerName: String
    ) {
        lock.synchronize {
            logFile.openWrite(append = true).bufferedWriter().use {
                it.append(loggerName)
                    .append("[").append(tags.entries.joinToString(",") { "${it.key}=${it.value}" })
                    .append("]")
                    .append(": ")
                    .appendLine(message)
                if (exception != null) {
                    it.appendLine(exception.stackTraceToString())
                }
            }
        }
    }
}
